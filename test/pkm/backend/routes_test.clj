(ns pkm.backend.routes-test
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [pkm.backend.db :as db]
            [pkm.backend.routes :as routes]))

(defn- parse-body
  "支援字串或串流的 JSON 解析輔助程式"
  [response]
  (let [body (:body response)]
    (cond
      (string? body) (json/parse-string body true)
      (nil? body) nil
      :else (json/parse-stream (io/reader body) true))))

(defn test-handler-fixture []
  (let [conn (db/init-db! {:store {:backend :memory :id (java.util.UUID/randomUUID)}
                           :keep-history? true})]
    (routes/create-app-handler conn)))

(deftest test-api-routes
  (let [handler (test-handler-fixture)]

    (testing "1. POST /api/notes 建立新筆記 (包含 Wiki 連結)"
      (let [payload {:title "Clojure Intro"
                     :content "Learn [[Datahike]] and [[Reitit]]"}
            request {:request-method :post
                     :uri "/api/notes"
                     :headers {"content-type" "application/json"}
                     :body (java.io.ByteArrayInputStream. (.getBytes (json/generate-string payload) "UTF-8"))}
            response (handler request)]
        (is (= 201 (:status response)))))
    (testing "2. GET /api/notes 列出所有筆記"
      (let [request {:request-method :get
                     :uri "/api/notes"}
            response (handler request)
            body (parse-body response)
            titles (set (map :title body))]
        (is (= 200 (:status response)))
        (is (<= 1 (count body)))
        (is (contains? titles "Clojure Intro"))))

    (testing "3. GET /api/notes/:title 讀取詳情"
      (let [request {:request-method :get
                     :uri "/api/notes/Clojure%20Intro"}
            response (handler request)
            body (parse-body response)]
        (is (= 200 (:status response)))
        (is (= "Clojure Intro" (or (:note/title body) (get body (keyword "note/title")) (:title body))))))

    (testing "4. POST /api/notes 不符合 Malli 規格應回傳 400"
      (let [invalid-payload {:title "" :content "Empty title"}
            request {:request-method :post
                     :uri "/api/notes"
                     :headers {"content-type" "application/json"}
                     :body (java.io.ByteArrayInputStream. (.getBytes (json/generate-string invalid-payload) "UTF-8"))}
            response (handler request)]
        (is (= 400 (:status response)))))))
