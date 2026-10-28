(ns pkm.backend.parser)

(defn extract-wiki-links
  "從 Markdown 內文提取 [[Wiki Link]], 轉為 Datahike Lookup Refs"
  [content]
  (if (empty? content)
    []
    (->> (re-seq #"\[\[(.*?)\]\]" content)
         (map second)
         distinct
         (map (fn [title] [:note/title title]))
         vec)))
