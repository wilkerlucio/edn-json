(ns com.wsscode.edn<->json-test
  (:require [clojure.spec.alpha :as s]
            [clojure.test :refer [is are run-tests testing deftest]]
            [clojure.test.check :as tc]
            [clojure.test.check.clojure-test :as test :include-macros true]
            [clojure.test.check.properties :as props]
            [clojure.walk :as walk]
            [com.wsscode.edn<->json :as cj]
            [clojure.string :as str]))

(defn =js [a b]
  (= (js/JSON.stringify a)
     (js/JSON.stringify b)))

(deftest test-clj->json
  (testing "simple types"
    (is (= (cj/edn->json 0) 0))
    (is (= (cj/edn->json 0.5) 0.5))
    (is (= (cj/edn->json -42) -42))
    (is (= (cj/edn->json true) true))
    (is (= (cj/edn->json false) false))
    (is (= (cj/edn->json nil) nil))
    (is (= (cj/edn->json "") ""))
    (is (= (cj/edn->json "hello") "hello")))

  (testing "edn encoding"
    (is (=js (cj/edn->json :keyword) "__edn-value|:keyword"))
    (is (=js (cj/edn->json :ns/keyword) "__edn-value|:ns/keyword"))
    (is (=js (cj/edn->json 'symb) "__edn-value|symb"))
    (is (=js (cj/edn->json 'foo/symb) "__edn-value|foo/symb"))

    (testing "default extensions"
      (=js (cj/edn->json #uuid"ca37585a-73cb-48c3-a8a4-7868ebc31801") "__edn-value|#uuid\"ca37585a-73cb-48c3-a8a4-7868ebc31801\"")
      (=js (cj/edn->json #inst"2020-01-08T03:20:26.984-00:00") "__edn-value|#inst \"2020-01-08T03:20:26.984-00:00\"")))

  (testing "sequences"
    (is (=js (cj/edn->json []) #js []))
    (is (=js (cj/edn->json [42]) #js [42]))
    (is (=js (cj/edn->json #{true}) #js ["__edn-list-type|set", true]))
    (is (=js (cj/edn->json '(nil :kw)) #js ["__edn-list-type|list", nil, "__edn-value|:kw"])))

  (testing "map encoding"
    (is (=js (cj/edn->json {}) #js {}))
    (is (=js (cj/edn->json {2 42}) #js {"2" 42}))
    (is (=js (cj/edn->json {nil 42}) #js {"__edn-key|nil" 42}))
    (is (=js (cj/edn->json {true 42}) #js {"__edn-key|true" 42}))
    (is (=js (cj/edn->json {"foo" 42}) #js {"foo" 42}))
    (is (=js (cj/edn->json {:foo 42}) #js {":foo" 42}))
    (is (=js (cj/edn->json {:foo/bar 42}) #js {":foo/bar" 42}))
    (is (=js (cj/edn->json {:foo {:bar 42}}) #js {":foo" #js {":bar" 42}}))

    ; maps with complex keys
    (is (=js (cj/edn->json {'sym 42}) #js {"__edn-key|sym" 42}))
    (is (=js (cj/edn->json {[3 5] 42}) #js {"__edn-key|[3 5]" 42}))
    (is (=js (cj/edn->json {#{:a :c} 42}) #js {"__edn-key|#{:c :a}" 42}))))

(defn sanitize-data [x]
  (walk/postwalk
    (fn [x]
      (cond
        (map? x)
        (into
          {}
          (map (fn [[k v]]
                 [(cond
                    (number? k)
                    (str k)

                    (= k ":")
                    :k

                    (and (string? k) (str/starts-with? k ":"))
                    (keyword (subs k 1))

                    :else
                    k)
                  v]))
          x)

        (and (number? x) (js/isNaN x))
        "NaN"

        :else
        x))
    x))

(defn valid-encode-decode []
  (props/for-all [x (s/gen any?)]
    (let [x (sanitize-data x)]
      (= x
         (-> x cj/edn->json cj/json->edn)))))

(test/defspec encode-and-decode-consistency {:max-size 12 :num-tests 5000} (valid-encode-decode))

(comment
  (transient #{})
  (sanitize-data {:foo 0})
  (let [x [{":" 0}]]
    (-> x cj/edn->json cj/json->edn))

  (tc/quick-check 50000 (valid-encode-decode) :max-size 12))
