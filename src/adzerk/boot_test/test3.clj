(ns adzerk.boot-test.test3
  (:use clojure.test))

(deftest i-always-fail-in-circle-too
  (is (= 0 1)))
