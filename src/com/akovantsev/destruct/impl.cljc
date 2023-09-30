(ns com.akovantsev.destruct.impl
  #?(:cljs (:require-macros [com.akovantsev.destruct.impl :refer [locals-map]]))
  (:refer-clojure :exclude [some])
  (:require
   [#?(:clj clojure.pprint :cljs cljs.pprint) :refer [pprint]]
   [clojure.set :as set]
   [clojure.walk :as walk]))

#?(:clj
    (defmacro locals-map []
      ;; https://gist.github.com/noisesmith/3490f2d3ed98e294e033b002bc2de178
      (let [ks (if (->> &env :ns)
                 (->> &env :locals keys (remove #{'_}))
                 (->> &env keys (remove #{'_})))]
        (zipmap (map #(list 'quote %) ks) ks))))


(defn spy [x] (pprint x) x)

(defn quoted? [form] (and (seq? form) (= (first form) 'quote)))

(def notmap? (complement map?))
(defn mbgensym [x] (if (symbol? x) x (gensym)))
(defn -notag [x] (with-meta x (-> x meta (dissoc :tag))))


;;         true false nil ::none
;; Truthy  +    -     -   -       {:a (ort x y 1)} -> (ort false )
;; Some    +    +     -   -       {:a (ors x y 1)} -> (ors nil  false 1) -> false
;; Present +    +     +   -       {:a (orp x y 1)} -> (orp ::none nil 1) -> nil
;; Any     +    +     +   +       {:a x}


(defmacro ort ([] nil) ([x] x) ([x & ys] (let [v (gensym "ort__")] `(let [~v ~x] (if (= ~v ::none) (ort ~@ys) (if ~v         ~v (ort ~@ys)))))))
(defmacro ors ([] nil) ([x] x) ([x & ys] (let [v (gensym "ors__")] `(let [~v ~x] (if (= ~v ::none) (ors ~@ys) (if (some? ~v) ~v (ors ~@ys)))))))
(defmacro orp ([] nil) ([x] x) ([x & ys] (let [v (gensym "orp__")] `(let [~v ~x] (if (= ~v ::none) (orp ~@ys) ~v)))))


(defn render [x]  (if   (= x ::none) nil x))


(defn subv* [x]   (cond (= x ::none) ::none (nil? x) ::none (= x []) nil :else (subvec x 1)))
(defn pop*  [x]   (cond (= x ::none) ::none (nil? x) ::none (= x []) nil :else (pop x)))
(defn next* [x]   (cond (= x ::none) ::none :else (next x)))
(defn peek* [x]   (cond (= x ::none) ::none (empty? x) ::none :else (peek x)))
(defn get*  [x k] (if   (= x ::none) ::none (if-let [[k v] (find x k)] v ::none)))
(defn nth*  [x i] (cond (= x ::none) ::none (empty? x) ::none
                        (pos? i) (recur (next* x) (dec i))
                        :zero    (first x)))


;; so this is to reduce text(!) footprint of a macroexpansion, to avoid hitting jvm's limit:
;; https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.9.1
;; > The value of the code_length item must be less than 65536.
;; and because cljs does not have an clojure.core/alias.
(defn aliases-map! []
  {;::none  (gensym "none__") ;; is inside of macro, so not gonna be replaced by walk.
   `subv*  (gensym "subv__")
   `pop*   (gensym "pop__")
   `next*  (gensym "next__")
   `peek*  (gensym "peek__")
   `get*   (gensym "get__")
   `nth*   (gensym "nth__")
   `render (gensym "ren__")})


(defn maybe-assoc [x]
  (let [foos (fn [x]
               (reduce
                 (fn [[acc seqs soms] el]
                   (let [[a b c] (maybe-assoc el)]
                     [(conj acc a) (merge seqs b) (merge soms c)]))
                 [[] nil nil] x))
        [x* seqs soms]
        (cond
          (quoted? x) [x nil nil]
          (vector? x) (-> x foos (update 0 vec))
          (seq? x)    (-> x foos (update 0 seq))
          (set? x)    (-> x foos (update 0 set))
          (notmap? x) [x nil nil]
          :map
          (let [[m LET COND]
                (reduce-kv
                  (fn rkv [[m LET COND] k v]
                    (let [[k* kss ks] (maybe-assoc k)
                          [v* vss vs] (maybe-assoc v)
                          seqs (concat kss vss)
                          soms (concat ks vs)
                          lets (->> (concat seqs soms)
                                 (remove (partial apply =))   ;; (let [sym sym])
                                 (sort-by #(-> % first name)) ;; (deep first)
                                 (reduce into []))
                          pred (concat
                                 (->> soms (map first) (map #(list `some? %)))
                                 (->> seqs (map first) (map #(list `seq %))))
                          pred* (if (-> pred count (= 1))
                                  (first pred)
                                  (apply list 'and pred))]
                      (if (empty? pred)
                        [(assoc m k* v*) LET COND]
                        [m (into LET lets) (conj COND pred* (list `assoc k* v*))])))
                  [nil [] []]
                  x)
                m* (cond
                     (empty? COND) m
                     (empty? LET)  (apply list `cond-> m COND)
                     :else         (list `let LET
                                     (apply list`cond-> m COND)))]
            [m* nil nil]))]
    (cond
      (-> x meta :tag (= '??)) (let [sym (-notag (mbgensym x))] [sym (assoc seqs sym (-notag x*)) soms])
      (-> x meta :tag (= '?))  (let [sym (-notag (mbgensym x))] [sym seqs (assoc soms sym (-notag x*))])
      :else
      [x* seqs soms])))



(defn vec-destr [form]
  (loop [todo form
         seps []
         done []
         temp []]
    (if (seq todo)
      (let [[x & todo-] todo
            done+ (conj done temp)
            seps+ (conj seps x)
            temp+ (conj temp x)]
        (if ('#{& |} x)
          (recur todo- seps+ done+ [])
          (recur todo- seps done temp+)))
      (let [done (conj done temp)
            [a b c] done
            [s1 s2] seps
            t0   (-> form meta :tag)
            t1   (-> s1 meta :tag)
            t2   (-> s2 meta :tag)]
        (case (count seps)
          0 {:A t0 :L a}
          2 {:A t0 :L a :M b :R c :T t1 :H t2}
          1 (case (first seps)
              & {:A t0 :L a :T (first b)}
              | {:A t0 :H (first a) :R b})
          (throw (ex-info "only up to 2 separators allowed" {'form form})))))))


(defn -get-deps [expr]
  (->> expr
    (tree-seq coll? seq)
    (filter symbol?)
    (remove #{`next* `get* `nth* `pop* `peek* `subv* 'orp 'ors 'ort})
    (into #{})))


(defn destruct [root path x]
  (let [tag      (-> x meta :tag)
        get-deps (fn [path] (-> path -get-deps (conj root)))]
    (cond
      (vector? x)
      (let [{:keys [A H T L M R] :as db} (vec-destr x)
            _  (assert (-> M count (< 2)) {'form x 'parsed db})
            M  (first M)
            nR (count R)
            nL (count L)
            ;; always new root:
            ;[root* path*] (if A [A []] [(gensym "vec__") []])
            [root* path*] [(gensym "vec__") []]]
        ;(spy db)

        (concat
          (if A
            (destruct A [] root*)
            (destruct root path root*))
          (when A
            (destruct root path A))
          (when (seq R)
            [{:root root*
              :path []
              :sym  root*
              :expr (list `vec root*)
              :deps #{root*}}])
          (when M
            (let [path** (vec (concat path* (repeat nR `pop*) (repeat nL `subv*)))]
              (destruct root* path** M)))
          (when H
            (let [path** (vec (concat path* (repeat nR `pop*)))]
              (destruct root* path** H)))
          (when T
            (let [path** (vec (concat path* (repeat nL `next*)))]
              (destruct root* path** T)))
          (mapcat
            (fn [idx sym]
              (let [path** (conj path* (list `nth* idx))]
                (destruct root* path** sym)))
            (range) L)
          (mapcat
            (fn [idx sym]
              (let [idx    (- nR idx)
                    path** (vec (concat path* (repeat (dec idx) `pop*) [`peek*]))]
                (destruct root* path** sym)))
            (range) R)))

      (map? x)
      (let [[root* path*] (if tag [tag []] [root path])
            destruct*     (fn [[k v]]
                            (if-let [ksym (-> k meta :tag)]
                              (cons
                                {:sym  ksym
                                 :expr (-notag k)
                                 :defs (-get-deps k)}
                                (destruct root* (conj path* (list `get* ksym)) v))
                              (destruct root* (conj path* (list `get* k)) v)))]
        (concat
          (mapcat destruct* x)
          (when tag
            (destruct root path tag))))

      (list? x)
      (let [[f sym & exprs] x]
        (assert ('#{ort ors orp} f) x)
        (let [path (cond-> path
                     exprs (conj (apply list f exprs)))]
          [{:root root
            :path path
            :sym  sym
            :expr (apply list '-> root path)
            :deps (get-deps path)}]))

      (symbol? x)
      (when-not (= x '_)
        [{:root root
          :path path
          :sym  x
          :expr (apply list '-> root path)
          :deps (get-deps path)}])

      :else
      (throw (ex-info (str "yo! " (type x) " " x) {})))))






(defn sort-by-deps [seen? xs]
  (let [syms (->> xs (map :sym) set)
        todo (->> xs (map #(update % :deps set/intersection syms)))]
    (loop [todo todo  seen? seen?  retry []  been []  done []]
      (let [[x & todo-] todo]
        (cond
          (empty? todo)
          (cond
            (empty? retry) done
            (= retry been) (throw (ex-info "deps loop: " {'seen seen? 'retry retry 'been been 'done done}))
            :else          (recur retry seen? [] retry done))

          (->> x :deps (every? seen?))
          (let [seen+  (conj seen? (:sym x))
                done+  (conj done x)]
            (recur todo- seen+ retry  been done+))

          :else
          (let [retry+ (conj retry x)]
            (recur todo- seen? retry+ been done)))))))


(defn replace-ors [pairs]
  (walk/postwalk-replace {'orp `orp 'ors `ors 'ort `ort} pairs))


(defn render-gets [pairs]
  (let [syms (map first pairs)
        rep  (fn [sym] (list `render sym))
        reps (zipmap syms (map rep syms))
        wf   (fn [x]
               (if
                 (and (list? x) (-> x first (= `get*)))
                 (walk/postwalk-replace reps x)
                 x))]
    pairs
    (walk/postwalk wf pairs)))