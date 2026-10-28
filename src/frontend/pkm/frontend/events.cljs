(ns pkm.frontend.events
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [re-frame.core :as rf]))

;; 1. 初始化資料庫狀態
(rf/reg-event-db
 :initialize-db
 (fn [_ _]
   {:notes/list []
    :notes/current nil
    :editor/form {:title "" :content ""}
    :loading? false}))

;; 2. 非同步事件：從後端 API 取得筆記清單
(rf/reg-event-fx
 :notes/fetch-list
 (fn [{:keys [db]} _]
   (go (let [response (<! (http/get "/api/notes"))]
         (if (= 200 (:status response))
           (rf/dispatch [:notes/set-list (:body response)])
           (js/console.error "Failed to fetch notes:" response))))
   {:db (assoc db :loading? true)}))

(rf/reg-event-db
 :notes/set-list
 (fn [db [_ notes]]
   (assoc db :notes/list notes :loading? false)))

;; 3. 非同步事件：取得單筆筆記詳情 (含 Backlinks)
(rf/reg-event-fx
 :notes/fetch-note
 (fn [{:keys [db]} [_ title]]
   ;; 補上 /api/notes/ 斜線，確保符合 Reitit /api/notes/:title 路由
   (go (let [response (<! (http/get (str "/api/notes/" (js/encodeURIComponent title))))]
         (if (= 200 (:status response))
           (rf/dispatch [:notes/set-current (:body response)])
           (js/console.error "Failed to fetch note:" response))))
   {:db (assoc db :loading? true)}))

(rf/reg-event-db
 :notes/set-current
 (fn [db [_ note]]
   (let [title (or (:note/title note) (:title note))
         content (or (:note/content note) (:content note))]
     (-> db
         (assoc :notes/current note)
         (assoc :editor/form {:title title :content content})
         (assoc :loading? false)))))

;; 4. 編輯器輸入更新 (事件名稱與 core.cljs 中的 :editor/set-field 一致)
(rf/reg-event-db
 :editor/set-field
 (fn [db [_ field value]]
   (assoc-in db [:editor/form field] value)))

;; 5. 非同步事件：儲存筆記
(rf/reg-event-fx
 :notes/save-current
 (fn [{:keys [db]} _]
   (let [{:keys [title content]} (:editor/form db)]
     (when (and (seq title) (seq content))
       (go (let [response (<! (http/post "/api/notes"
                                         {:json-params {:title title :content content}}))]
             (if (= 201 (:status response))
               (do
                 (rf/dispatch [:notes/fetch-list])
                 (rf/dispatch [:notes/fetch-note title]))
               (js/console.error "Failed to save note:" response))))))
   {:db (assoc db :loading? true)}))

;; 取得歷史修訂版本清單
(rf/reg-event-fx
 :history/fetch-versions
 (fn [{:keys [db]} [_ title]]
   (go (let [response (<! (http/get (str "/api/notes/" (js/encodeURIComponent title) "/history")))]
         (if (= 200 (:status response))
           (rf/dispatch [:history/set-versions (:body response)])
           (js/console.error "Failed to fetch history:" response))))
   {:db (assoc db :loading? true)}))

(rf/reg-event-db
 :history/set-versions
 (fn [db [_ versions]]
   (assoc db :history/versions versions :loading? false)))

;; 穿越至指定時間點 (呼叫後端 /api/history 快照 API)
(rf/reg-event-fx
 :history/travel-to
 (fn [{:keys [db]} [_ title timestamp]]
   (go (let [response (<! (http/get "/api/history"
                                    {:query-params {:title title :timestamp timestamp}}))]
         (if (= 200 (:status response))
           (rf/dispatch [:history/set-snapshot (:body response)])
           (js/console.error "Time travel failed:" response))))
   {:db (assoc db :loading? true)}))

;; 接收快照：同步更新 editor/form 與當時的反向連結拓撲
(rf/reg-event-db
 :history/set-snapshot
 (fn [db [_ snapshot]]
   (let [title (or (:note/title snapshot) (:title snapshot))
         content (or (:note/content snapshot) (:content snapshot))]
     (-> db
         (assoc :history/active-snapshot snapshot)
         (assoc :editor/form {:title title :content content})
         ;; 將反向連結也同步切換為該時間點的歷史狀態
         (assoc-in [:notes/current :note/backlinks]
                   (or (:note/backlinks snapshot)
                       (:notes/backlinks snapshot)
                       (:backlinks snapshot)
                       []))
         (assoc :loading? false)))))

;; 退出時光機返回現在：重新取得該筆記的最新 Live 狀態
(rf/reg-event-fx
 :history/exit-time-travel
 (fn [{:keys [db]} _]
   (let [title (or (get-in db [:notes/current :note/title])
                   (get-in db [:notes/current :title])
                   (get-in db [:editor/form :title]))]
     {:db (dissoc db :history/active-snapshot)
      :dispatch [:notes/fetch-note title]})))
