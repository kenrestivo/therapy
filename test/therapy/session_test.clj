(ns therapy.session-test
  (:use clojure.test
        [therapy.session :only [wrap-therapy-session-existing]]))

(deftest therapy-session
  (let [base-map {:uri "/foo" :request-method :get }]
    (testing "Put session value in")
    (is (= "bar" (get-in ((wrap-therapy-session-existing
                           #(assoc-in % [:session :foo] "bar"))
                          base-map)
                         [:session :foo])))
    (let [base-map (assoc base-map :session {:foo "bar"})]
      (testing "Pass session value through")
      (is (= "bar" (get-in ((wrap-therapy-session-existing identity)
                            base-map)
                           [:session :foo])))
      (testing "Change session value")
      (is  (= "baz" (get-in ((wrap-therapy-session-existing
                              #(assoc-in % [:session :foo] "baz"))
                             base-map)
                            [:session :foo])))
      (testing "Dissoc session value")
      (is  (not (contains? (:session
                            ((wrap-therapy-session-existing
                              #(assoc % :session (dissoc (:session %) :foo)))
                             base-map))
                           :foo))))
    (testing "Dissocing one key doesn't affect any others")
    (let [base-map (assoc base-map :session {:foo "bar" :quuz "auugh"})
          part-dissoc (:session
                       ((wrap-therapy-session-existing
                         #(assoc % :session (dissoc (:session %) :foo)))
                        base-map))]
      (is (not (contains? part-dissoc :foo)))
      (is (= "auugh" (:quuz  part-dissoc)))
      (testing "Changing one key doesn't affect any others")
      (let [part-change (:session
                         ((wrap-therapy-session-existing
                           #(assoc-in % [:session :foo] "baz"))
                          base-map))]
        (is (= "baz" (:foo part-change)))
        (is (= "auugh" (:quuz  part-change)))))
    (testing "Delete whole session.
     Ring takes nil to mean delete session, so it must get passed through.")
    (is (nil?  (:session ((wrap-therapy-session-existing
                           #(assoc % :session nil))
                          base-map))))
    (testing "Make sure the whole session goes away and stays away if deleted")
    (is (not (contains?  ((wrap-therapy-session-existing
                           #(dissoc % :session))
                          base-map)
                         :session)))))
