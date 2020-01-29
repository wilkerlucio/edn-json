(ns com.wsscode.edn<->json-test
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.string :as str]
            [clojure.test :refer [is are run-tests testing deftest]]
            [clojure.test.check :as tc]
            [clojure.test.check.clojure-test :as test :include-macros true]
            [clojure.test.check.properties :as props]
            [clojure.walk :as walk]
            [com.wsscode.edn<->json :as cj]))

#?(:cljs
   (defn =js [a b]
     (= (js/JSON.stringify a)
        (js/JSON.stringify b))))

#?(:cljs
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
       (is (=js (cj/edn->json {#{:a :c} 42}) #js {"__edn-key|#{:c :a}" 42})))))

(deftest test-clj->json-like
  (testing "simple types"
    (is (= (cj/edn->json-like 0) 0))
    (is (= (cj/edn->json-like 0.5) 0.5))
    (is (= (cj/edn->json-like -42) -42))
    (is (= (cj/edn->json-like true) true))
    (is (= (cj/edn->json-like false) false))
    (is (= (cj/edn->json-like nil) nil))
    (is (= (cj/edn->json-like "") ""))
    (is (= (cj/edn->json-like "hello") "hello")))

  (testing "edn encoding"
    (is (= (cj/edn->json-like :keyword) "__edn-value|:keyword"))
    (is (= (cj/edn->json-like :ns/keyword) "__edn-value|:ns/keyword"))
    (is (= (cj/edn->json-like 'symb) "__edn-value|symb"))
    (is (= (cj/edn->json-like 'foo/symb) "__edn-value|foo/symb"))

    (testing "default extensions"
      (= (cj/edn->json-like #uuid"ca37585a-73cb-48c3-a8a4-7868ebc31801") "__edn-value|#uuid\"ca37585a-73cb-48c3-a8a4-7868ebc31801\"")
      (= (cj/edn->json-like #inst"2020-01-08T03:20:26.984-00:00") "__edn-value|#inst \"2020-01-08T03:20:26.984-00:00\"")))

  (testing "sequences"
    (is (= (cj/edn->json-like []) []))
    (is (= (cj/edn->json-like [42]) [42]))
    (is (= (cj/edn->json-like #{true}) ["__edn-list-type|set", true]))
    (is (= (cj/edn->json-like '(nil :kw)) ["__edn-list-type|list", nil, "__edn-value|:kw"])))

  (testing "map encoding"
    (is (= (cj/edn->json-like {}) {}))
    (is (= (cj/edn->json-like {2 42}) {"2" 42}))
    (is (= (cj/edn->json-like {nil 42}) {"__edn-key|nil" 42}))
    (is (= (cj/edn->json-like {true 42}) {"__edn-key|true" 42}))
    (is (= (cj/edn->json-like {"foo" 42}) {"foo" 42}))
    (is (= (cj/edn->json-like {:foo 42}) {":foo" 42}))
    (is (= (cj/edn->json-like {:foo/bar 42}) {":foo/bar" 42}))
    (is (= (cj/edn->json-like {:foo {:bar 42}}) {":foo" {":bar" 42}}))

    ; maps with complex keys
    (is (= (cj/edn->json-like {'sym 42}) {"__edn-key|sym" 42}))
    (is (= (cj/edn->json-like {[3 5] 42}) {"__edn-key|[3 5]" 42}))
    (is (= (cj/edn->json-like {#{:a :c} 42}) {"__edn-key|#{:c :a}" 42})))

  (testing "options"
    (testing "::encode-list-type?"
      (is (= (cj/edn->json-like #{true} {::cj/encode-list-type? false})
             [true])))

    (testing "::encode-values?"
      (is (= (cj/edn->json-like 42 {::cj/encode-values? false})
             42))
      (is (= (cj/edn->json-like "" {::cj/encode-values? false})
             ""))
      (is (= (cj/edn->json-like :keyword {::cj/encode-value str})
             ":keyword"))
      (is (= (cj/edn->json-like :keyword {::cj/encode-value identity})
             :keyword))
      (is (= (cj/edn->json-like #{:keyword} {::cj/encode-value str})
             ["__edn-list-type|set" ":keyword"])))))

(deftest json-like->edn-test
  (testing "non string keys are maintained"
    (is (= (cj/json-like->edn {:foo "bar"})
           {:foo "bar"}))))

(defn is-nan? [x]
  #?(:clj  false
     :cljs (and (number? x) (js/isNaN x))))

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

        (is-nan? x)
        "NaN"

        :else
        x))
    x))

#?(:cljs
   (defn valid-encode-decode []
     (props/for-all [x (gen/fmap sanitize-data (s/gen any?))]
       (= x (-> x cj/edn->json cj/json->edn)))))

#?(:cljs
   (test/defspec encode-and-decode-consistency {:max-size 12 :num-tests 5000}
     (valid-encode-decode)))

(defn valid-encode-decode-json-like []
  (props/for-all [x (gen/fmap sanitize-data (s/gen any?))]
    (= x (-> x cj/edn->json-like cj/json-like->edn))))

(test/defspec encode-and-decode-consistency-json-like {:max-size 12 :num-tests 5000}
  (valid-encode-decode-json-like))

#?(:cljs
   (defn consistent-json-and-json-like-props []
     (props/for-all [x       (gen/fmap sanitize-data (s/gen any?))
                     options (s/gen (s/keys :opt [::cj/encode-list-type?
                                                  ::cj/encode-value]))]
       (= (-> x (cj/edn->json options) js->clj)
          (-> x (cj/edn->json-like options))))))

#?(:cljs
   (test/defspec consistent-json-and-json-like {:max-size 12 :num-tests 5000}
     (consistent-json-and-json-like-props)))

(comment
  (gen/sample (s/gen (s/keys :opt [::cj/encode-list-type?])) 100)

  (cj/json-like->edn (cj/edn->json-like {":foo" 42}))

  (transient #{})
  (sanitize-data {:foo 0})
  (let [x [{":" 0}]]
    (-> x cj/edn->json cj/json->edn))

  (tc/quick-check 50000 (valid-encode-decode) :max-size 12)
  (tc/quick-check 50000 (valid-encode-decode-json-like) :max-size 12)
  (tc/quick-check 50000 (consistent-json-and-json-like-props) :max-size 12))
