(ns pkm.backend.schema)

(def note-schema
  [;; 筆記 UUID
   {:db/ident :note/id
    :db/valueType :db.type/uuid
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/identity}

   ;; 筆記標題
   {:db/ident :note/title
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/identity}

   ;; 筆記內文
   {:db/ident :note/content
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}

   ;; ref
   {:db/ident :note/links
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many}

   ;; 時間戳記
   {:db/ident :note/updated-at
    :db/valueType :db.type/instant
    :db/cardinality :db.cardinality/one}])
