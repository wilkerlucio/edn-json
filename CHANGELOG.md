# Change Log

## [1.0.3]
- Decode JSON like will keep non string keys as is.

## [1.0.2]
- Add `edn->json-like` and `json-like->edn`, which work similar to the `edn->json`, but
returns Clojure data that's massaged to work nice as JSON, this works on CLJ and CLJS
- Add support for `::encode-list-type?` option.

## [1.0.1]
- BREAKING: change encoding, using string based encoding instead of object base, this change was
due to problems detected when using the object method, changing while its soon, should not
change later.

## [1.0.0]
- Initial Release
