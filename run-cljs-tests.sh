#!/bin/bash
rm -rd cljs-test-runner-out && clojure -M:cljs -r com\.akovantsev\.destruct\.test.* -x node
