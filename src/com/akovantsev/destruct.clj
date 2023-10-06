(ns com.akovantsev.destruct
  (:require
   [clojure.set :as set]
   [clojure.walk :as walk]
   [com.akovantsev.destruct.impl :as impl]
   [clojure.pprint :refer [pprint]]))



(defmacro maybe-assoc [x]
  (first (impl/maybe-assoc x)))


(defn destruct [root-sym form]
  (->> form
    (impl/destruct root-sym [])
    (impl/sort-by-deps #{root-sym})
    (map (fn [m] [(:sym m) (:expr m)]))
    (impl/replace-ors)
    (impl/render-gets)))


(defmacro => [input-form destr-form & body]
  (assert (-> body count odd?))
  (let [sym      (gensym "root__")
        pairs    (destruct sym destr-form)
        aliases  (impl/aliases-map!)
        renders  (->> pairs
                   (map first)
                   (distinct)
                   (mapv (fn [sym] [sym (list `impl/render sym)])))
        bindings (concat [[sym input-form]] pairs renders)
        replaced (walk/postwalk-replace aliases bindings)
        used     (->> bindings (tree-seq coll? seq) (filter aliases))
        bindings (reduce into []
                   (concat
                     (-> aliases (select-keys used) set/map-invert)
                     replaced))
        js?      (-> &env :ns boolean)
        print?   (-> &form meta :tag)
        coords   (-> &form meta (select-keys [:line]))]
    (when print?
      (println coords "bindings:")
      (pprint bindings))
    (list 'let bindings
      (when print?
        (list 'do
          (list `println coords "locals:")
          (if js?
            `(cljs.pprint/pprint (impl/locals-map))
            `(clojure.pprint/pprint (impl/locals-map)))))
      (if (-> body count (= 1))
        `~(first body)
        `(=> ~@body)))))


(defmacro =>> [destr-form & bodies-and-input-form]
  ;; (⌐ ͡■ ͜ʖ ͡■)
  (=> bodies-and-input-form
    [bodies | input-form]
    (with-meta
      `(=> ~input-form ~destr-form ~@bodies)
      (meta &form))))

