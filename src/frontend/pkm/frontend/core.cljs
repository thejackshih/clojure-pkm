(ns pkm.frontend.core
  (:require [pkm.frontend.events]
            [pkm.frontend.subs]
            [re-frame.core :as rf]
            [reagent.dom.client :as rdc]))

(defn time-travel-panel [title]
  (let [versions @(rf/subscribe [:history/versions])
        snapshot @(rf/subscribe [:history/active-snapshot])
        active? @(rf/subscribe [:history/active?])]
    [:div {:style {:backgroundColor "#fef3c7"
                   :border "1px solid #f59e0b"
                   :borderRadius "8px"
                   :padding "12px 16px"
                   :marginBottom "16px"}}
     [:div {:style {:display "flex" :justifyContent "space-between" :alignItems "center"}}
      [:span {:style {:fontWeight "bold" :color "#92400e"}}
       "⏳ 時光機 (Time-Travel / Datahike as-of 快照)"]
      [:div {:style {:display "flex" :gap "8px"}}
       [:button {:style {:padding "4px 10px" :cursor "pointer"}
                 :on-click #(rf/dispatch [:history/fetch-versions title])}
        "載入修訂歷史"]
       (when active?
         [:button {:style {:padding "4px 10px"
                           :backgroundColor "#dc2626"
                           :color "#fff"
                           :border "none"
                           :borderRadius "4px"
                           :cursor "pointer"}
                   :on-click #(rf/dispatch [:history/exit-time-travel])}
          "返回現在 (Live)"])]]

     (when (seq versions)
       [:div {:style {:marginTop "10px"}}
        [:label {:style {:fontSize "13px" :color "#78350f"}} "選擇歷史快照節點："]
        [:div {:style {:display "flex" :gap "8px" :flexWrap "wrap" :marginTop "6px"}}
         (for [{:keys [timestamp iso]} versions]
           [:button {:key (str timestamp)
                     :style {:fontSize "12px"
                             :padding "4px 8px"
                             :backgroundColor (if (= (:timestamp snapshot) timestamp) "#d97706" "#fff")
                             :color (if (= (:timestamp snapshot) timestamp) "#fff" "#333")
                             :border "1px solid #d97706"
                             :borderRadius "4px"
                             :cursor "pointer"}
                     :on-click #(rf/dispatch [:history/travel-to title timestamp])}
            iso])]])

     (when active?
       [:div {:style {:marginTop "8px" :fontSize "13px" :color "#b45309"}}
        "⚠️ 目前正處於唯讀歷史快照狀態。"])]))

;; 1. 左側側邊欄：筆記清單
(defn sidebar-view []
  (let [notes @(rf/subscribe [:notes/list])]
    [:aside {:style {:width "260px"
                     :borderRight "1px solid #e0e0e0"
                     :padding "16px"
                     :height "100vh"
                     :boxSizing "border-box"
                     :backgroundColor "#fcfcfc"}}
     [:h3 {:style {:marginTop 0 :color "#333"}} "📚 筆記庫 (PKM)"]
     [:button {:style {:width "100%"
                       :padding "10px"
                       :marginBottom "16px"
                       :backgroundColor "#2563eb"
                       :color "#fff"
                       :border "none"
                       :borderRadius "4px"
                       :fontWeight "bold"
                       :cursor "pointer"}
               :on-click #(rf/dispatch [:notes/set-current {:title "新筆記" :content ""}])}
      "+ 新增筆記"]
     [:ul {:style {:listStyle "none" :padding 0 :margin 0}}
      (for [{:keys [title id]} notes]
        [:li {:key (str id) :style {:margin "6px 0"}}
         [:a {:href "#"
              :style {:textDecoration "none"
                      :color "#1d4ed8"
                      :display "block"
                      :padding "6px 8px"
                      :borderRadius "4px"}
              :on-click (fn [e]
                          (.preventDefault e)
                          (rf/dispatch [:notes/fetch-note title]))}
          title]])]]))

;; 2. 反向連結卡片 (Backlinks Panel)
(defn backlinks-view []
  (let [backlinks @(rf/subscribe [:notes/current-backlinks])]
    [:div {:style {:marginTop "32px"
                    :paddingTop "20px"
                    :borderTop "2px dashed #cbd5e1"}}
     [:h4 {:style {:color "#475569" :marginBottom "12px"}}
      "🔗 反向連結 (Backlinks / 引用此篇的筆記)"]
     (if (empty? backlinks)
       [:p {:style {:color "#94a3b8" :fontSize "14px"}}
        "尚無其他筆記引用此內容。在其他筆記內文寫入 [[此標題]] 即可建立雙向關聯。"]
       [:div {:style {:display "flex" :flexDirection "column" :gap "10px"}}
        (for [{:keys [id title content]} backlinks]
          [:div {:key (str id)
                 :style {:padding "14px"
                         :backgroundColor "#f8fafc"
                         :borderRadius "6px"
                         :border "1px solid #e2e8f0"}}
           [:strong
            [:a {:href "#"
                 :style {:color "#2563eb" :textDecoration "none"}
                 :on-click (fn [e]
                             (.preventDefault e)
                             (rf/dispatch [:notes/fetch-note title]))}
             title]]
           [:p {:style {:fontSize "13px" :color "#64748b" :margin "6px 0 0 0"}}
            content]])])]))

;; 3. 主要編輯器 (Editor)
(defn editor-view []
  (let [{:keys [title content]} @(rf/subscribe [:editor/form])
        active-history? @(rf/subscribe [:history/active?])
        loading? @(rf/subscribe [:loading?])]
    [:main {:style {:flex 1
                    :padding "32px"
                    :overflowY "auto"
                    :height "100vh"
                    :boxSizing "border-box"}}
     (when loading?
       [:div {:style {:color "#d97706" :marginBottom "12px" :fontSize "14px"}}
        "處理中..."])
     [:div {:style {:display "flex" :gap "12px" :marginBottom "16px"}}
      [:input {:type "text"
               :style {:fontSize "22px"
                       :fontWeight "bold"
                       :flex 1
                       :padding "10px 12px"
                       :border "1px solid #cbd5e1"
                       :borderRadius "6px"
                       :outline "none"}
               :disabled active-history?
               :placeholder "筆記標題..."
               :value (or title "")
               :on-change #(rf/dispatch [:editor/set-field :title (-> % .-target .-value)])}]
      [:button {:style {:padding "10px 24px"
                        :fontWeight "bold"
                        :cursor (if active-history? "not-allowed" "pointer")
                        :backgroundColor (if active-history? "#94a3b8" "#16a34a")
                        :color "#fff"
                        :border "none"
                        :borderRadius "6px"}
                :disabled active-history?
                :on-click #(rf/dispatch [:notes/save-current])}
       (if active-history? "唯讀歷史快照中" "儲存 (Save)")]]

     [time-travel-panel (or title "")]

     [:textarea {:style {:width "100%"
                         :height "340px"
                         :padding "14px"
                         :fontSize "15px"
                         :fontFamily "monospace"
                         :border (if active-history? "2px solid #f59e0b" "1px solid #cbd5e1")
                         :backgroundColor (if active-history? "#fffbeb" "#fff")
                         :borderRadius "6px"
                         :boxSizing "border-box"
                         :resize "vertical"
                         :outline "none"}
                 :readOnly active-history?
                 :placeholder "輸入 Markdown 內文，支援 [[雙向連結]] 語法，例如：學習 [[Clojure]] 與 [[Datahike]]..."
                 :value (or content "")
                 :on-change #(when-not active-history?
                               (rf/dispatch [:editor/set-field :content (-> % .-target .-value)]))}]
     [backlinks-view]]))

;; 4. 根視圖 (App Root Layout)
(defn app-root []
  [:div {:style {:display "flex"
                 :height "100vh"
                 :overflow "hidden"
                 :fontFamily "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"}}
   [sidebar-view]
   [editor-view]])

;; 5. React 19 掛載邏輯 (相容 Reagent 2.0.1 與 Shadow-CLJS 熱重載)
(defonce root-atom (atom nil))

(defn mount-root []
  (let [container (.getElementById js/document "app")]
    (when-not @root-atom
      (reset! root-atom (rdc/create-root container)))
    (rdc/render @root-atom [app-root])))

(defn init! []
  (rf/dispatch-sync [:initialize-db])
  (rf/dispatch [:notes/fetch-list])
  (mount-root))

(defn reload! []
  (mount-root))
