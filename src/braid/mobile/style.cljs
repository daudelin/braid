(ns braid.mobile.style
  (:require [garden.core :refer [css]]
            [garden.stylesheet :refer [at-import]]
            [garden.arithmetic :as m]
            [garden.units :refer [vw vh]]))

(defn tee [x]
  (println x) x)

(def styles
  (css [:body]

       (let [w "100px"]
         [:.sidebar
          {:background "black"
           :position "absolute"
           :top 0
           :bottom 0
           :padding-left "100vw"
           :margin-left "-100vw"
           :left "-100px"
           :width w
           :z-index 100
           }
          [:&.open :&.closed
           {:transition "transform 0.25s ease-in-out"}]
          [:&.closed
           {:transform "translate3d(0,0,0)"}]
          [:&.open
           {:transform (str "translate3d(" w ",0,0)")}]
          [:.content
           [:.group
            {:display "block"}
            [:img
             {:width "2rem"
              :height "2rem"
              :background "white"
              :border-radius "10px"
              :vertical-align "middle"}]
            [:.name
             {:display "inline-block"
              :color "white"}]]]])

       [:.page
        {:position "absolute"
         :top 0
         :left 0
         :right 0
         :bottom 0
         :overflow "hidden"
         :z-index 50
         }]

       (let [thread-margin 4 ;vw
             ]
         [:.panels
          {:height "100vh"
           :min-width "300vw"
           }
          [:.panel
           {:width (vw (- 100 thread-margin thread-margin))
            :height "100vh"
            :margin-right (vw thread-margin)
            :margin-left (vw thread-margin)
            :display "inline-block"
            :vertical-align "top"
            }]])))
