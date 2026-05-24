(ns com.readlater-test
  (:require [clojure.test :refer [deftest is]]
            [com.readlater.worker :as worker]))

(deftest example-test
  (is (= 4 (+ 2 2))))

(deftest detect-kind-test
  (is (= :video    (worker/detect-kind "https://www.youtube.com/watch?v=abc123")))
  (is (= :video    (worker/detect-kind "https://youtu.be/abc123")))
  (is (= :paper    (worker/detect-kind "https://arxiv.org/abs/2301.00001")))
  (is (= :thread   (worker/detect-kind "https://x.com/user/status/123456")))
  (is (= :thread   (worker/detect-kind "https://reddit.com/r/clojure/comments/abc/title")))
  (is (nil?        (worker/detect-kind "https://example.com/some-article"))))
