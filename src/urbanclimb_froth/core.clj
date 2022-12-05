(ns urbanclimb-froth.core
  (:require [clj-http.client :as client]
            [clj-systemtray.core :as tray])
  (:gen-class))

(def ^:private branch-uuids
  {:collingwood "8674E350-D340-4AB3-A462-5595061A6950"
   :west-end "690326f9-98ce-4249-bd91-53a0676a137b"
   :newstead "a3010228-dfc6-4317-86c0-3839ffdf3fd0"
   :milton "d969f1b2-0c9f-49a9-b2ac-d7775642f298"
   :townsville "31d5ce53-0ca1-40a5-aea6-65a72f786492"
   :blackburn "46e5373c-2310-4520-b576-ccb4e4ef548d"})

(def ^:private branches-of-interest
  [:collingwood :blackburn])

(def ^:private occupancy-url
  "https://portal.urbanclimb.com.au/uc-services/ajax/gym/occupancy.ashx")

(defn get-branch-status
  "Get the status of a given branch, named by keyword.

  The keyword should be one of collingwood, west-end, newstead,
  milton, townsville or blackburn."
  [branch]
  (if-let [branch-uuid (branch branch-uuids)]
    (let [response (client/get occupancy-url
                               {:accept :json :query-params {"branch" branch-uuid} :as :json})
          {:keys [status body]} response]
      (if (<= status 200)
        (let [{status :GoogleStatus froth :Status} body]
          {:status status :froth froth})
        (throw (ex-info "Non-2xx response getting occupancy"
                        {:status (:status response) :body (:body response)}))))
    (throw (ex-info "Unknown branch, must be a keyword :collingwood"
                    {:unknown-branch branch}))))

(defn get-all-branch-statuss
  "Get the status' of all the branches of interest

  The result will be a map mapping the branch name keyword to its
  status."
  []
  (zipmap branches-of-interest (map get-branch-status branches-of-interest)))

(defn format-label
  "Format the label for a menu item based on the branch and status."
  [branch status]
  (let [{:keys [status froth]} status]
    (str (name branch) ": " froth " (" status ")")))

(defn branch-menu-item
  "Construct a menu item for the given branch.

  `branch` should be a key value pair of the branch name and the
  status map."
  [branch]
  (let [branch-name (key branch)
        status (val branch)]
    (tray/menu-item (format-label branch-name status) #(%))))

(defn get-menu-items
  "Get all the menu items in the given popup menu."
  [popup-menu]
  (map #(.getItem popup-menu, %) (range (.getItemCount popup-menu))))

(defn make-tray-icon
  "Construct a tray icon."
  []
  (let [menu (apply tray/popup-menu (map branch-menu-item (get-all-branch-statuss)))]
    (tray/make-tray-icon! "urban_climb_logo.png" menu)))

(defn -main [& _]
  (let [tray-icon (make-tray-icon)
        popup-menu (.getPopupMenu tray-icon)
        menu-items (get-menu-items popup-menu)
        menu-item-by-branch (zipmap branches-of-interest menu-items)]
    (loop []
      ;; This doesn't need to update very often - every 10 minutes is
      ;; _plenty_. Lets be nice to poor old urban climb.
      (Thread/sleep (* 10 1000))
      (let [statuss (get-all-branch-statuss)]
        (doseq [[branch menu-item] menu-item-by-branch]
          (let [status (branch statuss)
                label (format-label branch status)]
            (.setLabel menu-item label))
          menu-item-by-branch))
      (recur))))
