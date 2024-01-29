(ns com.akovantsev.destruct.test
  #?(:cljs (:require-macros [com.akovantsev.destruct.test :refer [assert=]]))
  (:require
   [clojure.string :as str]
   [clojure.walk :as walk]
   [com.akovantsev.destruct :refer [=> =>> maybe-assoc]]
   [com.akovantsev.destruct.impl :as impl :refer [locals-map vec-destr subv* get* nth* pop* peek* next*]]))



(def NONE ::impl/none)

(defmacro assert=
  ([x y] (with-meta `(assert= nil ~x ~y) (meta &form)))
  ([msg x y]
   (let [pp (if (-> &env :ns boolean)
              'cljs.pprint/pprint
              'clojure.pprint/pprint)]
     (with-meta
       `(assert (= ~x ~y)
          (str ~msg
            "\n" (with-out-str (~pp ~x))
            "  not=\n"
            (with-out-str (~pp ~y))))
       (meta &form)))))


(defn reset-gensym [form]
  (let [!n  (atom {})
        r!  (fn replace [sym]
              (let [[prefix suffix] (-> sym name (str/split #"__+" 2))]
                (if-not suffix
                  sym
                  (let [n (or (get @!n sym)
                            (let [len (count @!n)]
                              (swap! !n assoc sym len)
                              len))]
                    (symbol (str prefix "__" n))))))]
    (walk/prewalk
      (fn replacesym [form]
        (if (and (symbol? form) (nil? (namespace form))) (r! form) form))
      form)))


(assert= (impl/ort NONE nil false 1) 1)
(assert= (impl/ort NONE nil false) false)
(assert= (impl/ort NONE nil) nil)
(assert= (impl/ort NONE) NONE)

(assert= (impl/ors NONE nil false 1) false)
(assert= (impl/ors NONE nil false) false)
(assert= (impl/ors NONE nil) nil)
(assert= (impl/ors NONE) NONE)

(assert= (impl/orp NONE nil false 1) nil)
(assert= (impl/orp NONE nil false) nil)
(assert= (impl/orp NONE nil) nil)
(assert= (impl/orp NONE) NONE)


(assert= (->> :a (get* {})) NONE)
(assert= (->> :a (get* nil)) NONE)
(assert= (->> :a (get* NONE)) NONE)
(assert= (->> :a (get* {:a nil})) nil)
(assert= (->> :a (get* {:a false})) false)
(assert= (->> :a (get* {:a 1})) 1)

(assert= (subv* NONE) NONE)
(assert= (subv* nil) NONE)
(assert= (subv* []) nil)
(assert= (subv* [1]) [])
(assert= (subv* [1 2]) [2])

(assert= NONE (nth* nil 0))
(assert= NONE (nth* nil 1))
(assert= NONE (nth* NONE 0))
(assert= NONE (nth* NONE 1))
(assert= NONE (nth* [] 0))
(assert= NONE (nth* [] 1))
(assert= NONE (nth* [] 2))
(assert= nil  (nth* [nil] 0))
(assert= NONE (nth* [nil] 1))
(assert= NONE (nth* [nil] 2))
(assert= 1    (nth* [1] 0))
(assert= NONE (nth* [1] 1))
(assert= NONE (nth* [1] 2))
(assert= 1    (nth* [1 2] 0))
(assert= 2    (nth* [1 2] 1))
(assert= NONE (nth* [1 2] 2))

(assert= (pop* []) nil)
(assert= (pop* [1]) [])
(assert= (pop* nil) NONE)
(assert= (pop* NONE) NONE)

(assert= (peek* []) NONE)
(assert= (peek* nil) NONE)
(assert= (peek* NONE) NONE)
(assert= (peek* [nil]) nil)
(assert= (peek* [1]) 1)

(assert= (next* NONE) NONE)
(assert= (next* nil) nil)
(assert= (next* []) nil)
(assert= (next* [1]) nil)

(assert= (-> nil (get* :a) (nth* 3) (pop*)) NONE)




(assert=
  (reset-gensym
    (->> {:c "yo" :a {:b (range 8)}}
      (=>> {z s :a {:b ^all [a b ^tail | middle ^head | x y z]}}
        (select-keys (locals-map) '[all head middle tail a b x y z]))))
  '{all     (0 1 2 3 4 5 6 7),
    head    [0 1 2 3 4],
    middle  [2 3 4],
    tail    (2 3 4 5 6 7)
    a 0 b 1 x 5 y 6 z 7})

(assert=
  (reset-gensym
    (-> {:c "yo" :a {:b [0 1 2 3 4]}}
      (=> {z s :a {:b ^all [a b ^tail | middle ^head | x y z]}}
        (select-keys (locals-map) '[all head middle tail a b x y z]))))
  '{all     [0 1 2 3 4],
    middle  [],
    head    [0 1],
    tail    (2 3 4)
    a 0 b 1 x 2 y 3 z 4})

(assert=
  (reset-gensym
    (-> {:c "yo" :a {:b [0 1 2 3]}}
      (=> {z s :a {:b ^all [a b ^tail | middle ^head | x y z]}}
        (select-keys (locals-map) '[all head middle tail a b x y z]))))
  '{all     [0 1 2 3]
    head    [0]
    middle  nil
    tail    (2 3)
    a 0 b 1 x 1 y 2 z 3})


(assert=
  (reset-gensym
    (-> {:c "yo" :a {:b [0 1 2]}}
      (=> {z s :a {:b ^all [a b ^tail | middle ^head | x y z]}}
        (select-keys (locals-map) '[all head middle tail a b x y z]))))
  '{all     [0 1 2]
    head    []
    middle  nil
    tail    (2)
    a       0
    b       1
    x       0
    y       1
    z       2})

(-> {:c "yo" :a {:b [0 1 2]}}
  (=> {z s :a {:b ^all [a b ^tail | middle ^head | x y z]}} (locals-map)))

(-> {:c "yo" :a {:b [0 1 2 3 :c]}}
  (=> {z s :a {:b ^all [a b ^tail | middle ^head | x y z]}} (locals-map)))


(-> {:c "yo" :a {:b [[0 1 2] [3 4 5] [6 7 :c]]}}
  (=> {y s :a {:b [foo & ^third [bar & y]]}} (locals-map)))

(=> {:a    1
     :b    2
     [1 2] 3
     [2 2] 4
     :e    {:d (range 10)}}
    {:a          a
     :b          b
     [a b]       c
     [(inc a) b] d
     :e          {:d [fst snd | mid | m n]
                  :x x
                  :y (orp y x d)}}
    [a b c d x y fst snd mid m n])


(=> {:a nil
     :b :c
     :c 1}
    {:a a
     :b b
     (or a b) x}
    [a b x])


(assert=
  (=> [1 2 3 4 5]
      [a b | (orp m :too-short) | x y z]
      [a b m x y z])
  [1 2 [] 3 4 5])


(assert= {:x  :yo}
  (=>
    {:key nil
     :a   1
     :b   2}
    {:key       k
     (orp k :b) (orp x :yo)}
    (maybe-assoc
      {:k ^? k :x x})))


(assert= [{:c 1} 2 1]
  (=> {:b 2 :a {:c 1}}
      {:b b :a ^m {:c (ort a b 1)}}
      [m b a]))


;; can give name to keys:
(assert= [1 2 3]
  (=> {:a 1 2 3}
      {:a a ^k (inc a) b}
      [a k b]))

(=> nil [x] x)
(=> nil [x &] x)
(=> nil [x & y] [x y])
(=> nil [x | y] [x y])
(let [x 1]
  (=> nil [| x] [ x]))

(=> nil {:a [| y]} [y])


(assert= '[(2 3) (:a 2 3)]
         (=> [1 2 3]
           [_ & xs]
           (conj xs :a) ys
           [xs ys]))

(assert= '[(2 3) (:a 2 3)]
         (->> [1 2 3]
           (=>>
             [_ & xs]
             (conj xs :a) ys
             [xs ys])))

#_
(walk/macroexpand-all
  '(=> {:b 2 :a {:c 1}}
     {:b b :a ^m {:c (ort a b 1)}}
     [m b a]))

(assert=
  [[0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17] 18 19]
  (=> (range 20)
    [head | x y]
    [head x y]))


(assert= (=> (range 5) [a & b] [a b]) [0 '(1 2 3 4)])
(assert= (=> (range 5) [a | b] [a b]) [[0 1 2 3] 4])
(assert= (=> (range 5) [a | | b] [a b]) [0 4])
(assert= (=> (range 5) [a & | b] [a b]) [0 4])
(assert= (=> (range 5) [a & & b] [a b]) [0 4])


(assert= [3 3 3]
  (=> {:a {:b {:c [1 2 3 4]}}}
      {:a {:b {:c [_ _ x]}}}
      [x x x]))



(=> {} ^a {:a ^m {:b [_ x y & z]}}  {:x x :y y :m m :z ^?? z :a a})

;(walk/macroexpand-all '(=> {:a (range 2)} {:a ^a [x | (orp y :x)]} [a x]))


;; does not throw:
(assert= 1
       (=> [1] [(orp x (throw (ex-info "" {})))] x))
;; throws:
(assert= :e
  (try (=> []  [(orp x (throw (ex-info "" {})))] x)
    (catch #?(:clj Exception :cljs :default) _
      :e)))


(vec-destr '^all [a b ^tail & mid])
(vec-destr '^all [mid ^head | x y])
(vec-destr '^all [a b ^tail | | x y])
(vec-destr '^all [a b])
(vec-destr '^all [mid | x y])

#_
(macroexpand-1
  '(=> {:a ^m {:b [_ x y & z]}}  {:x x :y y :z ^?? z}))

;; testing ^tags in nested => do not disappear:
(assert= [[1 {[2] 3}] 1 {[2] 3} [2] 3]
      (=> [1 {[2] 3}]
          foo
        (=> foo
            ^all
            [one ^two
                 {^k
                  [2] v}]
            [all one two k v])))

(macroexpand-1
  '(maybe-assoc {:x x :y y :m m :z ^?? z}))


(assert= ^:blet (=> {:a 1 1 :b} {:a x x y} y) :b)
(do ^:blet (=> {:a 1 1 :b} {:c x (or x 1) y} y))

#_(do ^?(=> {:a 1} {:a a a (orp b (throw (ex-info "yo" {})))} [a b]))

#_(clojure.walk/macroexpand-all
    '(do ^:blet ^? (=> {:a 1 1 :b} {:c x (orp x 1) y} y)))

#_(clojure.walk/macroexpand-all
    '(do (=> {:a 1 1 :b} {:c x (orp x 1) y} y)))

;; testing splice
(assert=
  (let [path [\a \b]]
    ^:blet
    (=> {\a      {\b 1}
         [\a \b] 2}

      {^p                       ;; key alias, excludes splice
       (in path)       x    ;; splice sym
       path            y    ;; no splice
       (in [\a \b])    z    ;; splice literal
       (in (pop path)) w    ;; eval, then splice
       \a              {(in [\b]) q}}  ;; nested

      [p x y z w q]))
  [[\a \b] 1 2 1 {\b 1} 1])


(walk/macroexpand-all '       (=> [] [a b c d e] a))
(walk/macroexpand-all '^:blet (=> [] [a b c d e] a))