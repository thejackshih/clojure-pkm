# 雙向連結與時間旅行筆記系統 (Mini PKM) 架構設計與技術選型

> [!IMPORTANT]
> **學習目標宣言 (Project Motivation)**
> 
> 本專案的**唯一核心目的**是為了深入學習與掌握 **Clojure / ClojureScript 全端生態中最獨特、不可替代的關鍵思維與架構概念**（如：Data-Driven 設計、Datalog 圖查詢、不可變狀態、純函數事件流、單一事實來源等），**而非為了解決真實業務需求或打造商業產品**。
> 
> 選題與技術選型的所有決策，均以「**能否最大化展現 Clojure 與主流技術（TS/React/SQL）的根本差異**」為最高準則，刻意挑選最能凸顯該生態核心優勢的題目。

---

## 1. 核心學習維度與概念對照

| 專案模組 | 實作功能 | 欲學習掌握的 Clojure 核心概念 | 與主流技術 (TS/React/SQL) 的本質差異 |
| :--- | :--- | :--- | :--- |
| **資料庫 (DB)** | 筆記雙向引用 (`[[Wiki]]`) | **Datalog 宣告式圖查詢** 與 **EAVT 模型** | 不需寫關聯表或遞迴 CTE，純邏輯模式匹配直接遍歷圖拓撲。 |
| **資料持久化** | 時光機 (Time-Travel) | **Append-Only 不可變資料庫哲學** | 歷史狀態永不覆蓋，`(d/as-of db time)` 零成本瞬切任意歷史快照。 |
| **後端路由** | REST API 與參數驗證 | **Data-Driven 路由 (Reitit)** 與 **Malli** | 路由與規格只是純 Vector/Map 資料結構，可編譯、可運算、前後端共用。 |
| **前端狀態** | 編輯器與即時關聯響應 | **re-frame 單向資料流 (微型 Datomic)** | 狀態 100% 集中於不可變 `app-db`，徹底杜絕 React Hooks 閉包過期與依賴地獄。 |
| **前端視圖** | 筆記 Markdown 渲染 | **Hiccup 純資料 UI (Reagent)** | 不需 JSX 轉譯，用純 Vector 資料結構與標準函數（`map`、`filter`）操作畫面。 |

---

## 2. 端到端技術棧選型 (Tech Stack)

| 層次 | 選用技術 | 職責與學習價值 |
| :--- | :--- | :--- |
| **資料庫 (Database)** | **Datahike** | 嵌入式 (In-process)、檔案型儲存、**原生支援 Datalog 圖查詢與不可變時間旅行**，本機免安裝任何外部 DB 服務。 |
| **後端 HTTP (Server)** | **Ring + Ring-Jetty** | 輕量嵌入式 Servlet 伺服器，將 HTTP 抽象為純函式 `(fn [req] resp)`。 |
| **後端路由 (Routing)** | **Reitit (`reitit-ring`)** | 純資料導向（Data-driven）路由分發、中介層管理，兼具極致效能與可檢測性。 |
| **資料規格 (Schema/Coercion)**| **Malli** | 宣告式資料驗證，自動對請求參數轉型，錯誤時自動生成標準回應。 |
| **前端編譯 (Bundler)** | **shadow-cljs** | 編譯 ClojureScript、原生整合 npm 套件、提供毫秒級熱重載（Hot-reload）與前後端共用 REPL。 |
| **前端視圖 (View)** | **Reagent (基於 React)** | 使用 **Hiccup**（純 Vector 資料結構）撰寫 HTML，依賴細粒度 RAtom 自動追蹤更新。 |
| **前端狀態 (State)** | **re-frame** | 前端微型 Datomic 架構（單一不可變 `app-db`），事件（Events）、副作用（Effects）、訂閱（Subscriptions）徹底解耦。 |
| **前後端通訊協定** | **Transit / JSON** | 傳輸保留 Clojure 豐富型別（UUID、Keyword、Instant、Set）。 |

---

## 3. 資料模型設計 (EAVT Schema)

Datahike 採用屬性導向（Attribute-oriented）的圖模型，無需傳統關聯資料庫的中介表（Join Table）：

```clojure
(ns pkm.schema)

(def schema
  [;; 筆記識別 UUID
   {:db/ident       :note/id
    :db/valueType   :db.type/uuid
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity}

   ;; 筆記標題（作為 Wiki Link 唯一錨點）
   {:db/ident       :note/title
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity}

   ;; 筆記內文 (Markdown)
   {:db/ident       :note/content
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}

   ;; 雙向關聯指標：指向其他筆記實體的 Ref (圖結構核心)
   {:db/ident       :note/links
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/many}

   ;; 最後更新時間戳記
   {:db/ident       :note/updated-at
    :db/valueType   :db.type/instant
    :db/cardinality :db.cardinality/one}])
```

---

## 4. 關鍵演算法與資料庫查詢設計

### A. 雙向反向連結查詢 (Datalog Backlinks Query)
透過 Datalog 的模式匹配，一行搞定圖節點反向遍歷：

```clojure
(defn find-backlinks [db target-title]
  (d/q '[:find ?source-title ?source-id ?content
         :in $ ?target-title
         :where
         ;; 1. 鎖定被引用的目標實體
         [?target :note/title ?target-title]
         ;; 2. 模式匹配：誰的 :note/links 指向了 ?target
         [?source :note/links ?target]
         ;; 3. 提取引用者資訊
         [?source :note/title ?source-title]
         [?source :note/id ?source-id]
         [?source :note/content ?content]]
       db target-title))
```

### B. 內文 Wiki Link 解析與自動關聯
後端收到儲存請求時，自動解析內文的 `[[...]]`，轉換為 Datahike Lookup Refs：

```clojure
(defn extract-wiki-links [content]
  (->> (re-seq #"\[\[(.*?)\]\]" content)
       (map second)
       distinct
       (map (fn [title] [:note/title title]))))
```

### C. 時間旅行資料庫快照 (Time-Travel Snapshot)
```clojure
;; 給定歷史時間點 (java.util.Date 或 Instant)，一秒切換整座 DB 快照
(defn get-db-at [conn instant]
  (d/as-of @conn instant))
```

---

## 5. 建議的標準專案目錄結構

```text
my-pkm/
├── deps.edn                # 後端與全域 Clojure 依賴
├── shadow-cljs.edn         # 前端 ClojureScript 與 npm 編譯設定
├── package.json            # 前端依賴 (React, TailwindCSS 等)
│
├── resources/
│   └── public/
│       ├── index.html      # SPA 單頁骨架
│       └── css/style.css
│
└── src/
    ├── common/             # 前後端共用代碼 (.cljc)
    │   ├── routes.cljc     # Reitit 路由契約
    │   └── schema.cljc     # Malli 資料驗證規格
    │
    ├── backend/            # 後端 Clojure 代碼 (.clj)
    │   ├── core.clj        # 系統入口與 Jetty 伺服器啟動
    │   ├── db.clj          # Datahike 連線與 Schema 初始化
    │   ├── routes.clj      # Reitit 後端 API Handler (CRUD + Backlinks + History)
    │   └── parser.clj      # Wiki-link 解析邏輯
    │
    └── frontend/           # 前端 ClojureScript 代碼 (.cljs)
        ├── core.cljs       # 前端入口 (掛載 DOM)
        ├── db.cljs         # re-frame app-db 初始狀態
        ├── events.cljs     # 事件處理 (發送 API, 修改狀態, 時間旅行)
        ├── subs.cljs       # 訂閱推導 (當前筆記, Backlinks, 搜尋結果)
        └── views.cljs      # Reagent UI 元件 (編輯器, 側邊欄, 時間滑桿)
```

---

## 6. 學習導向的實作順序 (Milestones)

1. **建立專案基礎與依賴** (`deps.edn`, `shadow-cljs.edn`)：打通本機開發環境與 REPL。
2. **資料核心與 Datalog 引擎**：掌握 Datahike 嵌入式檔案連線、寫入 Schema、測試 Datalog 雙向查詢與歷史快照。
3. **Reitit 後端 API**：體會 Data-Driven 路由分發，定義共用 Malli 規格，開出 CRUD、Backlinks 與 History 端點。
4. **前端 Reagent + re-frame 骨架**：體會 Hiccup UI 繪製與 `app-db` 單向資料流，實作即時打字防手震自動存檔。
5. **時光機滑桿與閉環整合**：前端拖曳時間軸，後端調用 `(d/as-of)` 返回歷史快照，實現整座應用的時空穿越。
