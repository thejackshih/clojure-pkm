(ns pkm.backend.server
  (:require [pkm.backend.db :as db]
            [pkm.backend.routes :as routes]
            [ring.adapter.jetty :as jetty])
  (:gen-class))

(defonce server-atom (atom nil))

(defn start-server!
  ([] (start-server! 3000))
  ([port]
   (let [conn (db/init-db! db/default-db-config)
         handler (routes/create-app-handler conn)]
     (println (str "啟動 http://localhost:" port))
     (reset! server-atom
             (jetty/run-jetty handler {:port port :join? false})))))

(defn stop-server! []
  (when-let [server @server-atom]
    (.stop server)
    (reset! server-atom nil)
    (println "PKM Server 已停止")))

(defn -main [& _]
  (let [server (start-server! 3000)]
    (.join server)))
