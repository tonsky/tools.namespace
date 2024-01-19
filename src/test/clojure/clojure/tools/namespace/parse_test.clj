(ns clojure.tools.namespace.parse-test
  (:use [clojure.test :only (deftest is)]
        [clojure.tools.namespace.parse :only (deps-from-ns-decl
                                              read-ns-decl)]))

(def ns-decl-prefix-list
  '(ns com.example.one
     (:require (com.example two
                            [three :as three]
                            [four :refer (a b)])
               (com.example.sub [five :as five]
                                six))
     (:use (com.example seven
                        [eight :as eight]
                        (nine :only (c d))
                        [ten]))))

;; Some people like to write prefix lists as vectors, not lists. The
;; use/require functions accept this form.
(def ns-decl-prefix-list-as-vector
  '(ns com.example.one
     (:require [com.example
                two
                [three :as three]
                [four :refer (a b)]]
               [com.example.sub
                [five :as five]
                six
                [eleven :as-alias eleven]])
     (:use [com.example
            seven
            [eight :as eight]
            (nine :only (c d))
            [ten]])))

(def ns-decl-prefix-list-clauses-as-vectors
  "Sometimes people even write the clauses inside ns as vectors, which
  clojure.core/ns allows. See TNS-21."
  '(ns com.example.one
     [:require [com.example
                two
                [three :as three]
                [four :refer (a b)]]
      [com.example.sub
       [five :as five]
       six]]
     [:use [com.example
            seven
            [eight :as eight]
            (nine :only (c d))
            [ten]]]))

(def deps-from-prefix-list
  '#{com.example.two
     com.example.three
     com.example.four
     com.example.sub.five
     com.example.sub.six
     com.example.seven
     com.example.eight
     com.example.nine
     com.example.ten})

(deftest t-prefix-list
  (is (= deps-from-prefix-list
         (deps-from-ns-decl ns-decl-prefix-list))))

(deftest t-prefix-list-as-vector
  (is (= deps-from-prefix-list
         (deps-from-ns-decl ns-decl-prefix-list-as-vector))))

(deftest t-prefix-list-clauses-as-vectors
  (is (= deps-from-prefix-list
         (deps-from-ns-decl ns-decl-prefix-list-clauses-as-vectors))))

(deftest t-no-deps-returns-empty-set
  (is (= #{} (deps-from-ns-decl '(ns com.example.one)))))

(def ns-docstring-example
  "The example ns declaration used in the docstring of clojure.core/ns"
  '(ns foo.bar
     (:refer-clojure :exclude [ancestors printf])
     (:require (clojure.contrib sql combinatorics))
     (:use (my.lib this that))
     (:import (java.util Date Timer Random)
              (java.sql Connection Statement))))

(def deps-from-ns-docstring-example
  '#{clojure.contrib.sql
     clojure.contrib.combinatorics
     my.lib.this
     my.lib.that})

(deftest t-ns-docstring-example
  (is (= deps-from-ns-docstring-example
         (deps-from-ns-decl ns-docstring-example))))

(def require-docstring-example
  "The example ns declaration used in the docstring of
  clojure.core/require"
  '(ns (:require (clojure zip [set :as s]))))

(def deps-from-require-docstring-example
  '#{clojure.zip
     clojure.set})

(deftest t-require-docstring-example
  (is (= deps-from-require-docstring-example
         (deps-from-ns-decl require-docstring-example))))

(def multiple-clauses
  "Example showing more than one :require or :use clause in one ns
  declaration, which clojure.core/ns allows."
  '(ns foo.bar
     (:require com.example.one)
     (:import java.io.File)
     (:require (com.example two three))
     (:use (com.example [four :only [x]]))
     (:use (com.example (five :only [x])))))

(def deps-from-multiple-clauses
  '#{com.example.one
     com.example.two
     com.example.three
     com.example.four
     com.example.five})

(deftest t-deps-from-multiple-clauses
  (is (= deps-from-multiple-clauses
         (deps-from-ns-decl multiple-clauses))))

(def clauses-without-keywords
  "Example of require/use clauses with symbols instead of keywords,
  which clojure.core/ns allows."
  '(ns foo.bar
     (require com.example.one)
     (import java.io.File)
     (use (com.example (prefixes (two :only [x])
                                 three)))))

(def deps-from-clauses-without-keywords
  '#{com.example.one
     com.example.prefixes.two
     com.example.prefixes.three})

(deftest t-clauses-without-keywords
  (is (= deps-from-clauses-without-keywords
         (deps-from-ns-decl clauses-without-keywords))))

(def reader-conditionals-string
   "(ns com.examples.one
  (:require #?(:clj clojure.string
               :cljs goog.string)))")

(defn str->ns-decl [^String s]
  (-> s
      java.io.StringReader.
      java.io.PushbackReader.
      read-ns-decl))

(deftest t-reader-conditionals
  (when (resolve 'clojure.core/reader-conditional?)
    (let [actual (-> reader-conditionals-string
                     str->ns-decl
                     deps-from-ns-decl)]
      (is (= #{'clojure.string} actual)))))

(def cljs-ns-with-npm-dependency
  "(ns com.examples.one
    (:require [\"foobar\"] [baz]))")

(deftest cljs-string-dependency
  (let [actual (-> cljs-ns-with-npm-dependency
                   str->ns-decl
                   deps-from-ns-decl)]
    (is (= #{'baz} actual))))

(def cljs-ns-with-require-macros
  "(ns com.examples.one
    (:require-macros [org.macros :refer [my-macro]]))")

(deftest cljs-require-macros
  (let [actual (-> cljs-ns-with-require-macros
                   str->ns-decl
                   deps-from-ns-decl)]
    (is (= #{'org.macros} actual))))

(def multiple-clauses-str
  "(ns foo.bar)
   (require 'com.example.one)
   (import 'java.io.File)
   (require '(com.example two three))
   (use '(com.example [four :only [x]]))
   (use '(com.example (five :only [x])))")

(deftest t-deps-from-multiple-clauses-dynamic
  (let [actual (-> multiple-clauses-str
                   str->ns-decl
                   deps-from-ns-decl)]
    (is (= deps-from-multiple-clauses actual))))

(def in-ns-str
  "(in-ns 'com.example)
   (require 'com.example.one
            '[com.example.two])
   (use '[com.example three])
   (require ['com.example 'four]
            '[com.example [five]])")

(deftest t-in-ns
  (let [actual (-> in-ns-str
                   str->ns-decl
                   deps-from-ns-decl)]
    (is (= deps-from-multiple-clauses actual))))
