(ns adzerk.boot-test
  {:boot/export-tasks true}
  (:refer-clojure :exclude [test])
  (:require [boot.pod  :as pod]
            [boot.core :as core]))

(def pod-deps
  '[[org.clojure/tools.namespace "0.2.10" :exclusions [org.clojure/clojure]]
    [pjstadig/humane-test-output "0.6.0"  :exclusions [org.clojure/clojure]]])

(defn init [fresh-pod]
  (doto fresh-pod
    (pod/with-eval-in
     (require '[clojure.test :as t]
              '[clojure.java.io :as io]
              '[clojure.test.junit :as junit]
              '[pjstadig.humane-test-output :refer [activate!]]
              '[clojure.tools.namespace.find :refer [find-namespaces-in-dir]])
     (activate!)

     (defn all-ns* [& dirs]
       (mapcat #(find-namespaces-in-dir (io/file %)) dirs))

     (defn junit-plus-default-report [old-report junit-out m]
       (old-report m)
       (binding [t/*test-out* junit-out]
         (junit/junit-report m)))

     (defn run-tests-with-junit-reporter [run-tests-fn output-to]
       (let [junit-out-filename output-to
             old-report t/report]
         (with-open [junit-out (io/writer junit-out-filename)]
           (binding [junit/*var-context* (list)
                     junit/*depth* 1
                     t/report (partial junit-plus-default-report old-report junit-out)]
             (binding [*out* junit-out]
               (println "<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
               (println "<testsuites>"))
             (let [result  (run-tests-fn)]
               (binding [*out* junit-out]
                 (println "</testsuites>"))
               result)))))

     (defn test-ns* [pred junit-output-to ns]
       (binding [t/*report-counters* (ref t/*initial-report-counters*)]
         (let [ns-obj (the-ns ns)
               run-tests* (fn []
                            (t/do-report {:type :begin-test-ns :ns ns-obj})
                            (t/test-vars (filter pred (vals (ns-publics ns))))
                            (t/do-report {:type :end-test-ns :ns ns-obj})
                            @t/*report-counters*)]
           (if junit-output-to
             (run-tests-with-junit-reporter run-tests* (io/file junit-output-to (str (name ns) ".xml")))
             (run-tests*))))))))

;;; This prevents a name collision WARNING between the test task and
;;; clojure.core/test, a function that nobody really uses or cares
;;; about.
(if ((loaded-libs) 'boot.user)
  (ns-unmap 'boot.user 'test))

(core/deftask test
  "Run clojure.test tests in a pod.

  The --namespaces option specifies the namespaces to test. The default is to
  run tests in all namespaces found in the project.

  The --filters option specifies Clojure expressions that are evaluated with %
  bound to a Var in a namespace under test. All must evaluate to true for a Var
  to be considered for testing by clojure.test/test-vars."

  [n namespaces NAMESPACE #{sym} "The set of namespace symbols to run tests in."
   f filters    EXPR      #{edn} "The set of expressions to use to filter namespaces."
   j junit-output-to JUNIT-OUT str "The directory where a junit formatted report will be generated for each ns"]

  (let [worker-pods (pod/pod-pool (update-in (core/get-env) [:dependencies] into pod-deps) :init init)]
    (core/cleanup (worker-pods :shutdown))
    (core/with-pre-wrap fileset
      (let [worker-pod (worker-pods :refresh)
            namespaces (or (seq namespaces)
                           (pod/with-eval-in worker-pod
                             (all-ns* ~@(->> fileset
                                             core/input-dirs
                                             (map (memfn getPath))))))]
        (if (seq namespaces)
          (let [filterf `(~'fn [~'%] (and ~@filters))
                summary (pod/with-eval-in worker-pod
                          (doseq [ns '~namespaces] (require ns))
                          (when ~junit-output-to (io/make-parents ~junit-output-to "foo"))
                          (let [ns-results (map (partial test-ns* ~filterf ~junit-output-to) '~namespaces)]
                            (-> (reduce (partial merge-with +) ns-results)
                                (assoc :type :summary)
                                (doto t/do-report))))]
            (when (> (apply + (map summary [:fail :error])) 0)
              (throw (ex-info "Some tests failed or errored" summary))))
          (println "No namespaces were tested."))
        fileset))))
