(ns pkm.backend.db-test
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [datahike.api :as d]
            [pkm.backend.db :as db]))

(def test-db-config
  {:store {:backend :memory :id (java.util.UUID/randomUUID)}
   :keep-history? true})

(deftest test-datalog-backlinks-and-time-travel
  (let [conn (db/init-db! test-db-config)
        target-id (java.util.UUID/randomUUID)
        source-id (java.util.UUID/randomUUID)]
    (testing "1. 寫入被引用的目標筆記"
      (d/transact conn [{:note/id target-id
                         :note/title "Clojure"
                         :note/content "Lisp on JVM"}]))
    (let [time-before-link (java.util.Date.)]
      ;; 暫停 10 毫秒確保時間戳嚴格遞增
      (Thread/sleep 10)

      (testing "2. 寫入包含雙向關聯的來源筆記"
        (d/transact conn [{:note/id source-id
                           :note/title "PKM System"
                           :note/content "Built with [[Clojure]]"
                           ;; Lookup Ref: 直接透過唯一屬性指向目標實體
                           :note/links [[:note/title "Clojure"]]}]))

      (testing "3. Datalog 反向連結查詢"
        (let [backlinks (db/find-backlinks @conn "Clojure")]
          (is (= 1 (count backlinks)))
          (is (= "PKM System" (ffirst backlinks)))))

      (testing "4. 時間旅行"
        (let [historic-db (db/get-db-at conn time-before-link)
              past-backlinks (db/find-backlinks historic-db "Clojure")]
          ;;歷史快照尚未建立引用，故結果比須為空集合
          (is (empty? past-backlinks)))))))
