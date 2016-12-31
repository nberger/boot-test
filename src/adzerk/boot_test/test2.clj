(ns adzerk.boot-test.test2
  (:use clojure.test))

(deftest i-always-fail
  (is (= 0 1)))
