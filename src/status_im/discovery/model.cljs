(ns status-im.discovery.model
  ;status-im.models.discoveries
  (:require [status-im.utils.logging :as log]
            [status-im.persistence.realm :as realm]
            [status-im.persistence.realm :as r]))

(defn get-tag [tag]
  (log/debug "Getting tag: " tag)
  (-> (r/get-by-field :tag :name tag)
      (r/single-cljs)))

(defn decrease-tag-counter [tag]
  (let [tag        (:name tag)
        tag-object (get-tag tag)]
    (if tag-object
      (let [counter (dec (:count tag-object))]
        (if (zero? counter)
          (realm/delete tag-object)
          (realm/create :tag {:name  tag
                              :count counter}
                        true))))))

(defn increase-tag-counter [tag]
  (let [tag        (:name tag)
        tag-object (get-tag tag)]
    (if tag-object
      (realm/create :tag {:name  tag
                          :count (inc (:count tag-object))}
                    true))))

(defn decrease-tags-counter [tags]
  (doseq [tag tags]
    (decrease-tag-counter tag)))

(defn increase-tags-counter [tags]
  (doseq [tag tags]
    (increase-tag-counter tag)))

(defn get-tags [whisper-id]
  (:tags (-> (r/get-by-field :discoveries :whisper-id whisper-id)
             (r/single-cljs))))

(defn- create-discovery [{:keys [tags] :as discovery}]
  (log/debug "Creating discovery: " discovery tags)
  (realm/create :discoveries discovery true)
  (increase-tags-counter tags))

(defn- update-discovery [{:keys [whisper-id tags] :as discovery}]
  (let [old-tags (get-tags whisper-id)
        tags     (map :name tags)]
    (decrease-tags-counter old-tags)
    (realm/create :discoveries discovery true)
    (increase-tags-counter tags)))

(defn- discovery-exist? [discoveries discovery]
  (some #(= (:whisper-id discovery) (:whisper-id %)) discoveries))

(defn discovery-list []
  (->> (-> (r/get-all :discoveries)
           (r/sorted :last-updated :desc)
           r/collection->map)
       (map #(update % :tags vals))))

(defn- add-discoveries [discoveries]
  (realm/write (fn []
                 (let [db-discoveries (discovery-list)]
                   (mapv (fn [discovery]
                           (if-not (discovery-exist? db-discoveries
                                                     discovery)
                             (create-discovery discovery)
                             (update-discovery discovery)))
                         discoveries)))))

(defn save-discoveries [discoveries]
  (add-discoveries discoveries))

(defn discoveries-by-tag [tag limit]
  (let [discoveries (-> (r/get-by-filter :discoveries (str "tags.name = '" tag "'"))
                        (r/sorted :last-updated :desc))]
    (log/debug "Discoveries by tag: " tag)
    (if (pos? limit)
      (r/page discoveries 0 limit)
      discoveries)))

(defn all-tags []
  (-> (r/get-all :tag)
      (r/sorted :count :desc)
      r/collection->map))
