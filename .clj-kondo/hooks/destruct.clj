(ns hooks.destruct
  (:require [clj-kondo.hooks-api :as api]))


(defn collect-new-syms [x]
  (into #{}
    (cond
      (= x '|)    [x (-> x meta :tag)]
      (= x '&)    [x (-> x meta :tag)]
      ;;leaf
      (symbol? x) [x]
      ;; (orp|ors|ort new-sym ...):
      (list? x)   (take 2 x)
      (map? x)    (cons
                    (-> x meta :tag)
                    (mapcat collect-new-syms (vals x)))
      (vector? x) (cons
                    (-> x meta :tag)
                    (mapcat collect-new-syms x)))))


(defn -rewrite [destr-form body]
  (let [new-syms (-> destr-form api/sexpr collect-new-syms (disj nil) (concat ['? '??]))
        bindings (interleave
                   (map api/token-node new-syms)
                   (repeat (api/string-node "foo")))
        return   {:node (api/list-node
                          (list*
                            (api/token-node 'clojure.core/let)
                            (api/vector-node (vec bindings))
                            body))}]
    return))


(defn => [call-form]
  (let [[_input destr-form body :as args] (-> call-form :node :children rest)]
    (-rewrite destr-form body)))


(defn =>> [call-form]
  (let [[destr-form body _input :as args] (-> call-form :node :children rest)]
    (-rewrite destr-form body)))
