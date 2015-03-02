(ns phonecat-re-frame.core
    (:require [reagent.core :as reagent :refer [atom]]
              [reagent.session :as session]
              [secretary.core :as secretary :include-macros true]
              [goog.events :as events]
              [goog.history.EventType :as EventType]
              [cljsjs.react :as react]
              [re-frame.core :as re-frame])
    (:require-macros [reagent.ratom  :refer [reaction]])
    (:import goog.History))

;; -------------------------
;; Re-frame data

(re-frame/register-subs        ;; a new subscription handler
   :phones             ;; usage (subscribe [:phones])
   (fn [db]
     (reaction (:phones @db))))  ;; pulls out :phones

(re-frame/register-subs
 :search-input
 (fn [db]
   (reaction (:search-input @db))))

(re-frame/register-subs
 :order-prop
 (fn [db]
   (reaction (:order-prop @db))))

(re-frame/register-pure-handler
   :initialise-db             ;; usage: (dispatch [:initialise-db])
   (fn 
     [_ _]                   ;; Ignore both params (db and v). 
     {:phones [{:name "Nexus S" :snippet "Fast just got faster with Nexus S." :age 1}
               {:name "Motorola XOOM™ with Wi-Fi" :snippet "The Next, Next Generation tablet." :age 2}
               {:name "Motorola Xoom" :snippet "The Next, Next Generation tablet." :age 3}]
      :search-input ""
      :order-prop "name"}))

(defn handle-search-input-entered
  [app-state [_ search-input]]
  (assoc-in app-state [:search-input] search-input))

(defn handle-order-prop-changed
  [app-state [_ order-prop]]
  (assoc-in app-state [:order-prop] order-prop))

(re-frame/register-pure-handler
 :search-input-entered
 handle-search-input-entered)

(re-frame/register-pure-handler
 :order-prop-changed
 handle-order-prop-changed)
;; -------------------------
;; Views

(defn phone-component
  [name snippet]
  [:li
   [:span name]
   [:p snippet]])

(defn matches-query?
  [search-input phone]
  (if (= "" search-input)
    true
    (boolean (or
              (re-find (re-pattern search-input) (:name phone))
              (re-find (re-pattern search-input) (:snippet phone))))))

(defn phones-component
  []
  (let [phones (re-frame/subscribe [:phones])
        search-input (re-frame/subscribe [:search-input])
        order-prop (re-frame/subscribe [:order-prop])]
    (fn []
      [:ul {:class= "phones"}
       (for [phone (->> @phones
                        (filter (partial matches-query? @search-input))
                        (sort-by (keyword @order-prop)))]
         ^{:key phone} [phone-component (:name phone) (:snippet phone)])])))

(defn search-component
  []
  (let [search-input (re-frame/subscribe [:search-input])])
  (fn []
    [:div "Search"
     [:input {:on-change #(re-frame/dispatch [:search-input-entered (-> % .-target .-value)])}]]))

(defn mark-selected
  [props order-prop current-prop-value]
  (if (= order-prop current-prop-value)
    (reagent/merge-props props {:selected "selected"})
    props))

(defn order-by-component
  []
  (let [order-prop (re-frame/subscribe [:order-prop])]
    (fn []
      [:div "Sort by: "
       [:select {:on-change #(re-frame/dispatch [:order-prop-changed (-> % .-target .-value)])}
        [:option (mark-selected {:value "name"} @order-prop "Alphabetical") "Alphabetical"]
        [:option (mark-selected {:value "age"} @order-prop "Newest") "Newest"]]])))

(defn home-page []
  [:div {:class "container-fluid"}
   [:div {:class "row"}
    [:div {:class "col-md-2"}
     [search-component]]]
   [:div {:class "row"}
    [:div {:class "col-md-6"}
     [order-by-component]]]
   [:div {:class "row"}
    [:div {:class "col-md-10"}
     [phones-component]]]])

(defn current-page []
  [:div [(session/get :current-page)]])

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (session/put! :current-page #'home-page))


;; -------------------------
;; History
;; must be called after routes have been defined
(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
     EventType/NAVIGATE
     (fn [event]
       (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

;; -------------------------
;; Initialize app
(defn init! []
  (hook-browser-navigation!)
  (re-frame/dispatch [:initialise-db])
  (reagent/render-component [current-page] (.getElementById js/document "app")))
