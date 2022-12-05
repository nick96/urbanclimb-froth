(ns urbanclimb-froth.core
  (:require [clj-http.client :as client]
            [clj-systemtray.core :as tray])
  (:gen-class))

(def ^:private collingwood-branch-uuid
  "8674E350-D340-4AB3-A462-5595061A6950")

(def ^:private occupancy-url
  "https://portal.urbanclimb.com.au/uc-services/ajax/gym/occupancy.ashx")

(defn- collingwood [_])

(defn get-branch-status [branch]
  (let [branch-uuid (cond
                      (= branch :collingwood) collingwood-branch-uuid
                      :else (throw (ex-info "Unknown branch, must be a keyword :collingwood"
                                            {:unknown-branch branch})))
        response (client/get occupancy-url
                             {:accept :json :query-params {"branch" branch-uuid} :as :json})
        {:keys [status body]} response]
    (if (<= status 200)
      (let [{status :GoogleStatus froth :Status} body]
        {:status status :froth froth})
      (throw (ex-info "Non-2xx response getting occupancy"
                      {:status (:status response) :body (:body response)})))))

(defn -main
  "I don't do a whole lot ... yet."
  [& _]
  (let [collingwood-branch-status (get-branch-status :collingwood)
        menu
        (tray/popup-menu
         (tray/menu-item
          (str "Collingwood: "
               (:froth collingwood-branch-status)
               " ("
               (:status collingwood-branch-status)
               ")")
          collingwood))]
    (tray/make-tray-icon! "urban_climb_logo.png" menu)))
