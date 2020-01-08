(ns com.wsscode.edn<->json
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [goog.object :as gobj]))

(defn simple-js-type?
  "Return true for simple JS types. The intended use of this function is to detect if
  a value should be stored in its original form, for values that return false here, its
  expected that they get some encoding process before ending up as a JS value."
  [x]
  (or (string? x)
      (number? x)
      (boolean? x)
      (nil? x)
      (undefined? x)))

(defn encode-key
  [x]
  (cond
    (or (keyword? x)
        (number? x)
        (string? x))
    (str x)

    :else
    (str "__edn-key:" (pr-str x)  )))

(defn decode-key
  [s]
  (cond
    (str/starts-with? s ":")
    (keyword (subs s 1))

    (str/starts-with? s "__edn-key:")
    (edn/read-string (subs s (count "__edn-key:")))

    :else
    s))

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
  ([x options]
   (letfn [(thisfn [x]
             (cond
               (simple-js-type? x) x
               (satisfies? IEncodeJS x) (-clj->js x)
               (map? x) (let [m (js-obj)]
                          (doseq [[k v] x]
                            (gobj/set m (encode-key k) (thisfn v)))
                          m)
               (coll? x) (let [arr (array)]
                           (cond
                             (set? x)
                             (.push arr #js {"__edn-list-type" "set"})

                             (list? x)
                             (.push arr #js {"__edn-list-type" "list"}))

                           (doseq [x (map thisfn x)]
                             (.push arr x))
                           arr)
               :else #js {"__edn-value" (pr-str x)}))]
     (thisfn x))))

(defn js-obj? [x]
  (identical? (type x) js/Object))

(defn- list-type [x]
  (let [f (aget x 0)]
    (and (js-obj? f)
         (gobj/get f "__edn-list-type"))))

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
                   (case (list-type x)
                     "list"
                     (list)

                     "set"
                     #{}

                     [])
                   (map thisfn)
                   (cond->> (array-seq x)
                     lt (drop 1)
                     (= lt "list") (reverse))))

               (identical? (type x) js/Object)
               (let [edn-value (gobj/get x "__edn-value")]
                 (if edn-value
                   (edn/read-string edn-value)
                   (persistent!
                     (reduce
                       (fn [r k]
                         (assoc! r (decode-key k) (thisfn (gobj/get x k))))
                       (transient {}) (js-keys x)))))
               :else x))]
     (f x))))
