(ns pkm.backend.db
  (:require [datahike.api :as d]
            [pkm.backend.schema :refer [note-schema]]))

(def default-db-config
  {:store {:backend :memory :id #uuid "550e8400-e29b-41d4-a716-446655440000"}
   :keep-history? true})

(defn init-db!
  "初始化"
  [cfg]
  (d/create-database cfg)
  (let [conn (d/connect cfg)]
    (d/transact conn note-schema)
    conn))

(defn find-backlinks
  ""
  [db target-title]
  (d/q '[:find ?source-title ?source-id ?content
         :in $ ?target-title
         :where
         ;; 1. 鎖定被引用的目標實體
         [?target :note/title ?target-title]
         ;; 2. :note/links 指向 ?target
         [?source :note/links ?target]
         ;; 3. 提取引用者資訊
         [?source :note/title ?source-title]
         [?source :note/id ?source-id]
         [?source :note/content ?content]]
       db target-title))

(defn get-db-at
  "時間旅行"
  [conn instant]
  (d/as-of @conn instant))
