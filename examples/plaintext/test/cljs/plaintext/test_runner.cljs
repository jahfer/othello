(ns plaintext.test-runner
  (:require
   [doo.runner :refer-macros [doo-tests]]
   [plaintext.core-test]))

(enable-console-print!)

(doo-tests 'plaintext.core-test)
