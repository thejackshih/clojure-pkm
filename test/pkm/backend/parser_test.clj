(ns pkm.backend.parser-test
  (:require [clojure.test :refer [deftest is testing]]
            [pkm.backend.parser :as parser]))

(deftest test-extrct-wiki-links
  (testing "空內文應回應空值"
    (is (= [] (parser/extract-wiki-links ""))))

  (testing "提取多個 Wiki Links 並去重"
    (let [content "學習 [[Clojure]] 與 [[Datahike]], 還有更多 [[Clojure]] 的概念。"
          expected [[:note/title "Clojure"]
                    [:note/title "Datahike"]]]
      (is (= expected (parser/extract-wiki-links content))))))
