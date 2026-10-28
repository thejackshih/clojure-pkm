(ns pkm.frontend.subs
  (:require [re-frame.core :as rf]))

;; 訂閱全庫筆記清單
(rf/reg-sub
 :notes/list
 (fn [db _]
   (:notes/list db [])))

;; 訂閱當前查看的筆記詳情
(rf/reg-sub
 :notes/current-note
 (fn [db _]
   (:notes/current db)))

;; 訂閱當前筆記的反向連結 (Backlinks)
(rf/reg-sub
 :notes/current-backlinks
 (fn [db _]
   (let [curr (:notes/current db)]
     (or (:note/backlinks curr)
         (:notes/backlinks curr)
         (:backlinks curr)
         []))))

;; 訂閱 UI 編輯狀態
(rf/reg-sub
 :editor/form
 (fn [db _]
   (:editor/form db {:title "" :content ""})))

;; 訂閱載入狀態
(rf/reg-sub
 :loading?
 (fn [db _]
   (:loading? db false)))

;; 訂閱歷史版本列表
(rf/reg-sub
 :history/versions
 (fn [db _]
   (:history/versions db [])))

;; 訂閱是否處於時光機模式
(rf/reg-sub
 :history/active?
 (fn [db _]
   (boolean (:history/active-snapshot db))))

;; 訂閱當前查看的歷史快照
(rf/reg-sub
 :history/active-snapshot
 (fn [db _]
   (:history/active-snapshot db)))
