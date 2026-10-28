(ns pkm.backend.routes
  (:require [muuntaja.core :as m]
            [pkm.backend.db :as db]
            [pkm.backend.notes :as notes]
            [pkm.schema :as schema]
            [reitit.coercion.malli]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as coercion]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]))

;; 定義 HTTP 狀態常數
(def status-ok 200)
(def status-created 201)
(def status-not-found 404)

(defn create-app-routes [conn]
  ["/api"
   ["/notes"
    ;; GET /api/notes - 筆記清單
    {:get {:handler (fn [_]
                      {:status status-ok
                       :body (notes/list-all-notes @conn)})}
     ;; POST /api/notes - 建立或更新筆記(Malli 驗證 Request body)
     :post {:parameters {:body schema/NotePayload}
            :handler (fn [{{:keys [body]} :parameters}]
                       {:status status-created
                        :body (notes/save-note! conn body)})}}]

   ;; GET /api/notes/:title - 筆記詳情與 Backlinks
   ["/notes/:title"
    {:get {:parameters {:path [:map [:title :string]]}
           :handler (fn [{{{:keys [title]} :path} :parameters}]
                      (if-let [note (notes/get-note-detail @conn title)]
                        {:status status-ok :body note}
                        {:status status-not-found :body {:error "Note not found"}}))}}]
   ["/notes/:title/history"
    {:get {:parameters {:path [:map [:title :string]]}
           :handler (fn [{{{:keys [title]} :path} :parameters}]
                      {:status status-ok
                       :body (notes/get-note-history @conn title)})}}]
   ["/history"
    {:get {:parameters {:query [:map [:title :string] [:timestamp :int]]}
           :handler (fn [{{{:keys [title timestamp]} :query} :parameters}]
                      (let [past-time (java.util.Date. (long timestamp))
                            historic-db (db/get-db-at conn past-time)]
                        (if-let [note (notes/get-note-detail historic-db title)]
                          {:status status-ok :body note}
                          {:status status-not-found :body {:error "Historical note not found"}})))}}]])

(defn create-app-handler [conn]
  (ring/ring-handler
   (ring/router
    (create-app-routes conn)
    {:data {:coercion reitit.coercion.malli/coercion
            :muuntaja m/instance
            :middleware [parameters/parameters-middleware
                         muuntaja/format-middleware
                         coercion/coerce-exceptions-middleware
                         coercion/coerce-request-middleware
                         coercion/coerce-response-middleware]}})
   (ring/routes
    (ring/create-resource-handler {:path "/"})
    (ring/create-default-handler))))
