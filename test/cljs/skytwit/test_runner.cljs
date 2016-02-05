(ns skytwit.test-runner
  (:require
   [doo.runner :refer-macros [doo-tests]]
   [skytwit.core-test]))

(enable-console-print!)

(doo-tests 'skytwit.core-test)
