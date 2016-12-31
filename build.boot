(set-env!
 :source-paths #{"src"}
 :dependencies '[[adzerk/bootlaces "0.1.13" :scope "test"]])

(require '[adzerk.bootlaces :refer :all]
         '[adzerk.boot-test :refer [test]])

(def +version+ "1.1.2")

(bootlaces! +version+)

(task-options!
 pom  {:project     'adzerk/boot-test
       :version     +version+
       :description "Run some tests in boot!"
       :url         "https://github.com/adzerk/boot-test"
       :scm         {:url "https://github.com/adzerk/boot-test"}
       :license     {"Eclipse Public License"
                     "http://www.eclipse.org/legal/epl-v10.html"}}
 test {:namespaces '#{adzerk.boot-test.test
                      adzerk.boot-test.test2
                      adzerk.boot-test.test3}
       :junit-output-to "junit-out"})

;; deps task to cache dependencies in CircleCI
(deftask deps [])
