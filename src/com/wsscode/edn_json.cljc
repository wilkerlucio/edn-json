(ns com.wsscode.edn-json
  (:require [clojure.edn :as edn]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            #?(:cljs [goog.object :as gobj])))

(s/def ::encode-list-type? boolean?)
(s/def ::encode-value (s/with-gen (s/nilable (s/fspec :args (s/cat :x any?) :ret any?)) #(s/gen (s/nilable #{str}))))
(s/def ::encode-map-key (s/with-gen (s/nilable (s/fspec :args (s/cat :x any?) :ret any?)) #(s/gen (s/nilable #{str}))))

(s/def ::decode-edn-values? boolean?)

(defn simple-js-type?
  "Return true for simple JS types. The intended use of this function is to detect if
  a value should be stored in its original form, for values that return false here, its
  expected that they get some encoding process before ending up as a JS value."
  [x]
  (or (string? x)
      (number? x)
      (boolean? x)
      (nil? x)
      #?(:cljs (undefined? x))))

(defn encode-key
  "Encode map key, keywords numbers and strings are always encoded as strings. By
  default encode other keys using the prefix `__edn-key|` and the END encoded value.

  If you provide ::encode-map-key, this will be used instead of the default encode."
  [x {::keys [encode-map-key]}]
  (cond
    (or (keyword? x)
        (number? x)
        (string? x))
    (str x)

    :else
    (if encode-map-key
      (encode-map-key x)
      (str "__edn-key|" (pr-str x)))))

(defn decode-key
  "Decode string key to EDN, if key is not a string it is returned as is."
  [s]
  (if (string? s)
    (cond
      (str/starts-with? s ":")
      (keyword (subs s 1))

      (str/starts-with? s "__edn-key|")
      (edn/read-string (subs s (count "__edn-key|")))

      :else
      s)
    s))

(defn encode-value
  "Encode value representation, can be overwritten with the ::encode-value setting."
  [x {::keys [encode-value]}]
  (if encode-value
    (encode-value x)
    (str "__edn-value|" (pr-str x))))

(defn edn-value-str? [x]
  (and (string? x) (str/starts-with? x "__edn-value|")))

(defn decode-value
  [x {::keys [decode-edn-values?]
      :or    {decode-edn-values? true}}]
  (if (and decode-edn-values? (edn-value-str? x))
    (edn/read-string (subs x (count "__edn-value|")))
    x))

#?(:cljs
   (defn edn->json
     "Recursively transforms ClojureScript values to JavaScript.

     The encoded JSON will have extra notation to enable a better deserialization
     later, keeping complex keys, keyword keys (simple and qualified), also encode
     extended types like UUID and dates. Using json->edn you can restore the original
     data from the JSON.

     Some things that don't get restored:

     - metadata is lost
     - number keys on maps will be turned into strings on the conversion back

     Other than that, all printable values should encode and decode with fidelity."
     ([x] (edn->json x {}))
     ([x {::keys [encode-list-type?]
          :or    {encode-list-type? true}
          :as    opts}]
      (letfn [(thisfn [x]
                (cond
                  (and (string? x) (str/starts-with? x "__edn-value|")) (encode-value x opts)
                  (simple-js-type? x) x
                  (satisfies? IEncodeJS x) (-clj->js x)
                  (map? x) (let [m (js-obj)]
                             (doseq [[k v] x]
                               (gobj/set m (encode-key k opts) (thisfn v)))
                             m)
                  (coll? x) (let [arr (array)]
                              (if encode-list-type?
                                (cond
                                  (set? x)
                                  (.push arr "__edn-list-type|set")

                                  (list? x)
                                  (.push arr "__edn-list-type|list")))

                              (doseq [x (map thisfn x)]
                                (.push arr x))
                              arr)
                  :else (encode-value x opts)))]
        (thisfn x)))))

(defn edn->json-like
  "Same as edn->json, but returns Clojure maps that are JSON friendly instead of the
  JSON directly. This is useful as a middle format if you need to later send it encoded
  as JSON."
  ([x] (edn->json-like x {}))
  ([x {::keys [encode-list-type?]
       :or    {encode-list-type? true}
       :as    opts}]
   (letfn [(thisfn [x]
             (cond
               (and (string? x) (str/starts-with? x "__edn-value|")) (encode-value x opts)
               (simple-js-type? x) x
               (map? x) (into
                          {}
                          (map (fn [[k v]] [(encode-key k opts) (thisfn v)]))
                          x)
               (coll? x) (let [arr (if encode-list-type?
                                     (cond-> []
                                       (set? x)
                                       (conj "__edn-list-type|set")

                                       (list? x)
                                       (conj "__edn-list-type|list"))

                                     [])]
                           (into arr (map thisfn) x))
               :else (encode-value x opts)))]
     (thisfn x))))

#?(:cljs
   (defn js-obj? [x]
     (identical? (type x) js/Object)))

(defn- list-type* [x]
  (and (string? x)
       (str/starts-with? x "__edn-list-type|")
       (subs x (count "__edn-list-type|"))))

(defn- list-type [x]
  (let [f (aget x 0)]
    (list-type* f)))

#?(:cljs
   (defn json->edn
     "Recursively transforms JavaScript arrays into ClojureScript
     vectors, and JavaScript objects into ClojureScript maps.

     Use to get fidelity restore of JSON data encoded with edn->json."
     ([x] (json->edn x {}))
     ([x opts]
      (let [f (fn thisfn [x]
                (cond
                  (satisfies? IEncodeClojure x)
                  (-js->clj x (apply array-map opts))

                  (seq? x)
                  (doall (map thisfn x))

                  (map-entry? x)
                  (MapEntry. (thisfn (key x)) (thisfn (val x)) nil)

                  (coll? x)
                  (into (empty x) (map thisfn) x)

                  (array? x)
                  (let [lt (list-type x)]
                    (into
                      (case lt
                        "list"
                        (list)

                        "set"
                        #{}

                        [])
                      (map thisfn)
                      (cond->> (array-seq x)
                        lt (drop 1)
                        (= lt "list") (reverse))))

                  (edn-value-str? x)
                  (decode-value x opts)

                  (identical? (type x) js/Object)
                  (persistent!
                    (reduce
                      (fn [r k]
                        (assoc! r (decode-key k) (thisfn (gobj/get x k))))
                      (transient {}) (js-keys x)))

                  :else x))]
        (f x)))))

(defn json-like->edn
  "Recursively transforms JavaScript arrays into ClojureScript
  vectors, and JavaScript objects into ClojureScript maps.

  Use to get fidelity restore of JSON data encoded with edn->json."
  ([x] (json-like->edn x {}))
  ([x opts]
   (let [f (fn thisfn [x]
             (cond
               (seq? x)
               (doall (map thisfn x))

               (map? x)
               (persistent!
                 (reduce
                   (fn [r k]
                     (assoc! r (decode-key k) (thisfn (get x k))))
                   (transient {}) (keys x)))

               (coll? x)
               (let [lt (list-type* (first x))]
                 (into
                   (case lt
                     "list"
                     (list)

                     "set"
                     #{}

                     [])
                   (map thisfn)
                   (cond->> x
                     lt (drop 1)
                     (= lt "list") (reverse))))

               :else (decode-value x opts)))]
     (f x))))
