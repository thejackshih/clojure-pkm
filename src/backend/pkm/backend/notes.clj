(ns pkm.backend.notes
  (:require [datahike.api :as d]
            [pkm.backend.db :as db]
            [pkm.backend.parser :as parser]))

(defn- ensure-stub-notes!
  "若引用的目標筆記不存在，先行建立佔位筆記 (Stub Note)"
  [conn link-refs]
  (let [target-titles (map second link-refs)
        missing-titles (filterv (fn [title]
                                 (nil? (d/q '[:find ?e .
                                              :in $ ?title
                                              :where [?e :note/title ?title]]
                                            @conn title)))
                               target-titles)]
    (when (seq missing-titles)
      (d/transact conn
                  (mapv (fn [title]
                          {:note/id (java.util.UUID/randomUUID)
                           :note/title title
                           :note/content ""
                           :note/updated-at (java.util.Date.)})
                        missing-titles)))))

(defn save-note!
  "儲存或更新筆記，自動提取 Wiki Links 並建立圖關聯"
  [conn {:keys [title content]}]
  (let [links (parser/extract-wiki-links content)]
    ;; 1. 確保所有被引用的目標實體已存在
    (ensure-stub-notes! conn links)

    ;; 2. 儲存或更新主筆記
    (let [existing-id (d/q '[:find ?id .
                             :in $ ?title
                             :where
                             [?e :note/title ?title]
                             [?e :note/id ?id]]
                           @conn title)
          note-id (or existing-id (java.util.UUID/randomUUID))
          tx-data [{:note/id note-id
                    :note/title title
                    :note/content content
                    :note/links links
                    :note/updated-at (java.util.Date.)}]]
      (d/transact conn tx-data)
      {:id note-id :title title :content content})))

(defn get-note-detail
  "查詢筆記內文，並附帶所有反向引用(Backlinks)"
  [db title]
  (when-let [entity (d/pull db '[:note/id :note/title :note/content :note/updated-at]
                            [:note/title title])]
    (let [raw-backlinks (db/find-backlinks db title)
          backlinks (mapv (fn [[src-title src-id src-content]]
                            {:title src-title :id src-id :content src-content})
                          raw-backlinks)]
      (assoc entity :note/backlinks backlinks))))

(defn list-all-notes
  "列出全庫筆記標題與識別碼"
  [db]
  (let [results (d/q '[:find ?title ?id ?updated
                       :where
                       [?e :note/title ?title]
                       [?e :note/id ?id]
                       [?e :note/updated-at ?updated]]
                     db)]
    (mapv (fn [[title id updated]]
            {:title title :id id :updated-at updated})
          results)))

(defn get-note-history
  "查詢某篇筆記的所有歷史交易時間戳與版本"
  [db title]
  (let [history-db (d/history db)
        results (d/q '[:find ?tx-time ?content
                       :in $ ?title
                       :where
                       [?e :note/title ?title]
                       [?e :note/content ?content ?tx true]
                       [?tx :db/txInstant ?tx-time]]
                     history-db title)]
    (->> results
         (mapv (fn [[instant content]]
                 {:timestamp (.getTime instant)
                  :iso (str instant)
                  :content content}))
         (sort-by :timestamp))))
