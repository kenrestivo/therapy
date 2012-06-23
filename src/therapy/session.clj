(ns therapy.session
  (:refer-clojure :exclude [get remove swap!])
  (:use ring.middleware.session
        ring.middleware.flash))

;; ## Session

(declare ^:dynamic *therapy-session*)
(defonce mem (atom {}))

(defn put!
  "Associates the key with the given value in the session"
  [k v]
  (clojure.core/swap! *therapy-session* assoc k v))

(defn get
  "Get the key's value from the session, returns nil if it doesn't exist."
  ([k] (get k nil))
  ([k default]
    (clojure.core/get @*therapy-session* k default)))

(defn swap!
  "Replace the current session's value with the result of executing f with
   the current value and args."
  [f & args]
  (apply clojure.core/swap! *therapy-session* f args))

(defn clear!
  "Remove all data from the session and start over cleanly."
  []
  (reset! *therapy-session* {}))

(defn remove!
  "Remove a key from the session"
  [k]
  (clojure.core/swap! *therapy-session* dissoc k))

(defn get!
  "Destructive get from the session. This returns the current value of the key
   and then removes it from the session."
  ([k] (get! k nil))
  ([k default]
   (let [cur (get k default)]
     (remove! k)
     cur)))

(defn ^:private therapy-session [handler]
   "Store therapy session keys in a :therapy map, because other middleware that
    expects pure functions may delete keys, and simply merging won't work.
    Ring takes (not (contains? response :session) to mean: don't update session.
    Ring takes (nil? (:session resonse) to mean: delete the session.
    Because therapy-session mutates :session, it needs to duplicate ring/wrap-session
    functionality to handle these cases."
  (fn [request]
    (binding [*therapy-session* (atom (get-in request [:session :therapy] {}))]
      (remove! :_flash)
      (when-let [resp (handler request)]
        (if (=  (get-in request [:session :therapy] {})  @*therapy-session*)
          resp
          (if (contains? resp :session)
            (if (nil? (:session resp))
              resp
              (assoc-in resp [:session :therapy] @*therapy-session*))
            (assoc resp :session (assoc (:session request) :therapy @*therapy-session*))))))))

(defn wrap-therapy-session
  "Provides a stateful layer over wrap-params. Options are passed to wrap-session."
  [handler & [opts]]
  (-> handler
      therapy-session
      (wrap-session opts)))

(defn wrap-therapy-session-existing
  "Provides a stateful layer over wrap-params. Expects that wrap-params has
   already been used."
  [handler]
  (therapy-session handler))

;; ## Flash

(declare ^:dynamic *therapy-flash*)

(defn flash-put!
  "Store a value that will persist for this request and the next."
  [k v]
  (clojure.core/swap! *therapy-flash* assoc-in [:outgoing k] v))

(defn flash-get
  "Retrieve the flash stored value."
  ([k]
     (flash-get k nil))
  ([k not-found]
   (let [in (get-in @*therapy-flash* [:incoming k])
         out (get-in @*therapy-flash* [:outgoing k])]
     (or out in not-found))))

(defn ^:private therapy-flash [handler]
  (fn [request]
    (binding [*therapy-flash* (atom {:incoming (:flash request)})]
      (let [resp (handler request)
            outgoing-flash (:outgoing @*therapy-flash*)]
        (if (and resp outgoing-flash)
          (assoc resp :flash outgoing-flash)
          resp)))))

(defn wrap-therapy-flash
  "Provides a stateful layer over wrap-flash."
  [handler]
  (-> handler
      therapy-flash
      wrap-flash))

(defn wrap-therapy-flash-existing
  "Provides a stateful layer over wrap-flash. Expects that wrap-flash has
   already been used."
  [handler]
  (therapy-flash handler))
