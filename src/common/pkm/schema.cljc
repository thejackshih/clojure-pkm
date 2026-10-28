(ns pkm.schema
  (:require [malli.core :as m]))

;; 建立/更新筆記的 payload 規格 (純資料結構)
(def NotePayload
  [:map
   [:title [:string {:min 1 :max 100}]]
   [:content [:string]]])

;; 回傳筆記的完整實體規格
(def NoteEntity
  [:map
   [:id :uuid]
   [:title :string]
   [:content :string]
   [:updated-at [:maybe inst?]]])
