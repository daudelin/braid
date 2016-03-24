(ns braid.mobile.core
  (:require [reagent.core :as r]
            [re-frame.core :refer [subscribe dispatch]]
            [braid.mobile.state]
            [braid.mobile.style :refer [styles]]
            [braid.mobile.sidebar :refer [sidebar-view]]
            [braid.mobile.panels :refer [panels-view]]))

(enable-console-print!)

(defn message-view [message]
  [:div.message
   (:content message)])

(defn thread-view [thread]
  [:div.thread
   [:div.close "X"]
   (for [message (:messages thread)]
     ^{:key (message :id)}
     [message-view message])
   [:textarea {:value "asd"}]])

(defn inbox-view []
  (let [threads (subscribe [:active-group-inbox-threads])
        group (subscribe [:active-group])]
    (fn []
      ; TODO Why is this being rendered when sidebar is dragged?
      [:div.inbox.page
       [:div.threads
        [panels-view @threads thread-view]]])))

(defn main-view []
  [:div.main
   [sidebar-view]
   [inbox-view]])

(defn login-view []
  [:div.login
   [:input {:placeholder "email"}]
   [:input {:placeholder "password"}]
   [:button {:on-click (fn [_] (dispatch [:log-in!]))}
    "Let's Do This!"]])

(defn app-view []
  (let [logged-in? (subscribe [:logged-in?])]
    (fn []
      [:div.app
       [:style styles]
       (if @logged-in?
         [main-view]
         [login-view])])))

(defn init []
  (r/render [app-view] (.-body js/document)))

