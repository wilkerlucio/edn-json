(defproject com.wsscode/edn-json "1.0.4"
  :description "Tools to convert back and forth between EDN and JSON, optimized for consistency and storage."
  :url "https://github.com/wilkerlucio/edn-json"
  :license {:name "MIT"
            :url  "https://opensource.org/licenses/MIT"}

  :source-paths ["src"]

  :dependencies [[org.clojure/clojure "1.10.0" :scope "provided"]
                 [org.clojure/clojurescript "1.10.597" :scope "provided"]]

  :jar-exclusions [#"node-modules/.+"]

  :deploy-repositories [["releases" :clojars]]

  :aliases {"pre-release"  [["vcs" "assert-committed"]
                            ["change" "version" "leiningen.release/bump-version" "release"]
                            ["vcs" "commit"]
                            ["vcs" "tag" "v"]]

            "post-release" [["change" "version" "leiningen.release/bump-version"]
                            ["vcs" "commit"]
                            ["vcs" "push"]]})

