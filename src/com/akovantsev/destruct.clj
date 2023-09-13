(ns com.akovantsev.destruct
  (:require
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


(defmacro => [input-form destr-form body]
  (let [sym#      (gensym "root__")
        pairs#    (destruct sym# destr-form)
        bindings# (reduce into [sym# input-form] pairs#)
        renders#  (->> pairs#
                    (map first)
                    (distinct)
                    (mapv (fn [sym] [sym (list `impl/render sym)])))
        js?#      (-> &env :ns boolean)
        print?#   (-> &form meta :tag)
        coords#   (-> &form meta (select-keys [:line]))]
    (when print?#
      (println coords# "bindings:")
      (pprint bindings#))
    (list 'let bindings#
      (when print?#
        (list 'do
          (list `println coords# "locals:")
          (if js?#
            `(cljs.pprint/pprint (impl/locals-map))
            `(clojure.pprint/pprint (impl/locals-map)))))
      (list 'let (reduce into [] renders#)
        `(maybe-assoc ~body)))))


(defmacro =>> [destr-form body input-form]
  (with-meta
    `(=> ~input-form ~destr-form ~body)
    (meta &form)))

