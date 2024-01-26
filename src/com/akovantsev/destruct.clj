(ns com.akovantsev.destruct
  (:require
   [clojure.set :as set]
   [clojure.walk :as walk]
   [com.akovantsev.destruct.impl :as impl]))



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
  (let [print?   (-> &form meta :tag boolean)
        sym      (gensym "root__")
        pairs    (destruct sym destr-form)
        id       (name (gensym ""))
        aliases  (impl/aliases-map! id)
        renders  (->> pairs
                   (map first)
                   (distinct)
                   (mapv (fn [sym] [sym (list `impl/render sym)])))
        pairs+   (cond->> pairs
                   print? (map (fn [[sym expr]]
                                 [sym (list `impl/spy sym expr)])))
        bindings (concat [[sym input-form]] pairs+ renders)
        replaced (walk/postwalk-replace aliases bindings)
        used     (->> bindings (tree-seq coll? seq) (filter aliases))
        bindings (reduce into []
                   (concat
                     (-> aliases (select-keys used) set/map-invert)
                     replaced))]
    (list 'let bindings
      (if (-> body count (= 1))
        `~(first body)
        `^{:tag ~print?} (=> ~@body)))))


(defmacro =>> [destr-form & bodies-and-input-form]
  ;; (⌐ ͡■ ͜ʖ ͡■)
  (=> bodies-and-input-form
    [bodies | input-form]
    (with-meta
      `(=> ~input-form ~destr-form ~@bodies)
      {:tag (-> &form meta :tag boolean)})))
