(ns chat.test.server.asana-extension
  (:require [clojure.test :refer :all]
            [mount.core :as mount]
            [braid.server.conf :as conf]
            [chat.server.db :as db]
            [chat.server.extensions :as ext]
            [chat.server.extensions.asana :as asana]
            [chat.server.crypto :as crypto]))

(use-fixtures :each
              (fn [t]
                (-> (mount/only #{#'conf/config #'db/conn})
                    (mount/swap {#'conf/config
                                 {:db-url "datomic:mem://chat-test"}})
                    (mount/start))
                (t)
                (datomic.api/delete-database (conf/config :db-url))
                (mount/stop)))


(deftest subscribe-extension
  (let [group (db/create-group! {:id (db/uuid) :name "g1"})
        tag-1 (db/create-tag! {:id (db/uuid) :name "acme1" :group-id (group :id)})
        user-1 (db/create-user! {:id (db/uuid)
                          :email "foo@bar.com"
                          :password "foobar"
                          :avatar ""})
        thread-1-id (db/uuid)
        thread-2-id (db/uuid)
        ext (asana/create-asana-extension {:id (db/uuid)
                                           :user-name "asana bot"
                                           :group-id (group :id)
                                           :tag-id (tag-1 :id)})
        ext2-id (db/uuid)]
    (db/create-message! {:thread-id thread-1-id :id (db/uuid) :content "zzz"
                         :group-id (group :id) :user-id (user-1 :id)
                         :created-at (java.util.Date.)
                         :mentioned-tag-ids [(tag-1 :id)]})
    (db/create-message! {:thread-id thread-2-id :id (db/uuid) :content "zzz"
                         :group-id (group :id) :user-id (user-1 :id)
                         :created-at (java.util.Date.) :mentioned-tag-ids [(tag-1 :id)]})
    (testing "created extension has expected attributes"
      (is (nil? (:token ext)))
      (is (= (group :id) (:group-id ext)))
      (is (= :asana (ext :type)))
      (is (= (tag-1 :id) (get-in ext [:config :tag-id]))))
    (testing "can subscribe to threads"
      (db/extension-subscribe (ext :id) thread-1-id)
      (is (= #{thread-1-id} (set (:threads (db/extension-by-id (:id ext))))))
      (db/extension-subscribe (ext :id) thread-2-id)
      (is (= #{thread-1-id thread-2-id} (set (:threads (db/extension-by-id (:id ext)))))))
    (testing "can see which extensions are subscribed to a given thread"
      (is (= [(db/extension-by-id (ext :id))] (db/extensions-watching thread-1-id)))
      (let [ext2 (asana/create-asana-extension {:id ext2-id
                                                :user-name "asana bot 2"
                                                :group-id (group :id)
                                                :tag-id (tag-1 :id)})]
        (db/extension-subscribe (ext2 :id) thread-1-id)

        (is (= [(db/extension-by-id (ext :id))
                (db/extension-by-id (ext2 :id))]
               (db/extensions-watching thread-1-id)))

        (is (= [(db/extension-by-id (ext :id))] (db/extensions-watching thread-2-id)))))
    (testing "can see the extensions a group has"
      (db/user-add-to-group! (user-1 :id) (group :id))
      (is (= (assoc group :extensions [{:id (ext :id) :type :asana}
                                       {:id ext2-id :type :asana}])
             (db/get-group (group :id))
             (first (db/get-groups-for-user (user-1 :id))))))
    (testing "can destroy extensions"
      (asana/destroy-asana-extension (ext :id))
      (is (empty? (db/extensions-watching thread-2-id))))))

(deftest webhook-events
  (let [group (db/create-group! {:id (db/uuid) :name "g1"})
        tag-1 (db/create-tag! {:id (db/uuid) :name "acme1" :group-id (group :id)})
        user-1 (db/create-user! {:id (db/uuid)
                                 :email "foo@bar.com"
                                 :password "foobar"
                                 :avatar ""})
        thread-1-id (db/uuid)
        thread-2-id (db/uuid)
        ext (asana/create-asana-extension {:id (db/uuid)
                                           :user-name "asana bot"
                                           :group-id (group :id)
                                           :tag-id (tag-1 :id)})]
    (testing "gets an x-hook-secret on first request"
      (is (= 400 (:status (ext/handle-webhook ext {:headers {} :body ""}))))
      (let [msg "{\"foo\": \"bar\"}"]
        (is (thrown? AssertionError
                     (ext/handle-webhook
                       ext
                       {:body msg
                        :headers {"x-hook-signature" (crypto/hmac "foobar" msg)}}))))
      (let [handshake-resp (ext/handle-webhook ext
                                               {:headers {"x-hook-secret" "foobar"}
                                                :body ""})]
        (is (= 200 (:status handshake-resp)))
        (is (= "foobar" (get-in handshake-resp [:headers "X-Hook-Secret"])))))

    (testing "can use the secret to compute signature"
      (let [ext' (db/extension-by-id (ext :id))]
        (is (= "foobar" (get-in ext' [:config :webhook-secret])))
        (is (= 400 (:status (ext/handle-webhook ext' {:body "{\"foo\": \"bar\"}"
                                                      :headers {}}))))
        (is (= 400 (:status (ext/handle-webhook ext' {:body "{\"foo\": \"bar\"}"
                                                      :headers {"x-hook-signature" "zzzzz"}}))))
        (let [msg "{\"foo\": \"bar\"}"]
          (is (= 200
                 (:status
                   (ext/handle-webhook
                     ext'
                     {:body msg
                      :headers {"x-hook-signature" (crypto/hmac "foobar" msg)}})))))))))
