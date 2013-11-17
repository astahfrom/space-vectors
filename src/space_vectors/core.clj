(ns space-vectors.core
  (:gen-class)
  (:require [instaparse.core :as insta])
  (:import (java.text NumberFormat)
           (java.util Locale)))

;;;; Types
(defn as-vec [v] {:type :vector :comps v})
(defn as-line [op r] {:type :line :op op :r r})
(defn as-plane [n d] {:type :plane :n n :d d})
(defn as-pplane [op r1 r2] {:type :pplane :op op :r1 r1 :r2 r2})

;;;; Parsing

(declare normal)
(defn pplane->plane [a] (if (= (:type a) :pplane) (normal a) a))

(def transform-options
  {:number (fn [& ns] (read-string (apply str ns)))
   :vector (fn [& es] (as-vec (apply vector es)))
   :line (fn [op r] (as-line op r))
   :plane (fn [a b c d] (as-plane (as-vec [a b c]) d))
   :pplane (fn [op r1 r2] (as-pplane op r1 r2))
   :func (fn [f & xs] (apply (ns-resolve 'space-vectors.core (symbol f))
                             (->> xs (map pplane->plane) (sort-by :type))))
   :S identity})

(def parser
  (insta/parser
   "S = func

    <elm> = (vector | line | plane | pplane) | expr

    func = vecfunc1 (vector | expr)
         | vecfunc2 (vector | expr) <c> (vector | expr)
         | 'lwith' (line | expr) <c> number
         | planefunc (plane | pplane | expr)
         | 'pwith' (pplane | expr) <c> number number
         | 'line' (vector | expr) <c> (vector | expr)
         | 'plane' (vector | expr) <c> (vector | expr) [<c> (vector | expr)]
         | genfunc elm <c> elm

    <expr> = <lpar> func <rpar>

    <vecfunc1> = 'length' | 'normalize'
    <vecfunc2> = 'dotp' | 'cross' | 'area' | 'between'

    <planefunc> = 'param' | 'three-points' | 'normal'

    <genfunc> = 'angle' | 'parallel?' | 'perpendicular?' | 'distance' | 'on?' | 'intersection' | 'projection' | 'skewed?'

    vector = [space] [lparen] [space] number space number space number [space] [rparen] [space]

    line = [space] vector [space] [<plus>] [space] [<word>] [space] [<mult>] vector

    plane = [space] number [[space] [<mult>] [space] <'x'>] [space]
            number [[space] [<mult>] [space] <'y'>] [space]
            number [[space] [<mult>] [space] <'z'>] [space]
            number [[space]  <'='>   [space] <'0'>]

    pplane = [space] vector   [space] [<plus>] [space]
             [<word>] [space] [<mult>] [space]
             vector   [space] [<plus>] [space]
             [<word>] [space] [<mult>] [space]
             vector

    <word> = #'[a-z]'+

    <plus> = '+'
    <mult> = '*'
    <lpar> = [space] '(' [space]
    <rpar> = [space] ')' [space]
    <lparen> = <'('> | <'<'> | <'['>
    <rparen> = <')'> | <'>'> | <']'>
    <c> = [space] ',' [space]
    number = ('+' | '-' | '') [space] num [('.' | '/') num]
    <num> = #'[0-9]+'
    <space> = (<#'[ ]+'> | <','>)+"))

(defn parse
  "Parse the input and execute the functions."
  [input]
  (->> (parser input)
       (insta/transform transform-options)))

;;;; Vectors

(defn length
  "Length of the vector."
  [{v :comps}]
  (->> v (map #(Math/pow % 2)) (reduce +) Math/sqrt))

(defn normalize
  "Normalize the vector to a unit-vector."
  [v]
  (as-vec (mapv #(/ % (length v)) (:comps v))))

(defn dotp
  "Return the scalar product of two vectors."
  [{a :comps} {b :comps}]
  (reduce + (map * a b)))

(defn cross
  "Cross two vectors into a third."
  [{a :comps} {b :comps}]
  (let [[ax ay az] a, [bx by bz] b
         r [(- (* ay bz) (* az by))
            (- (* az bx) (* ax bz))
            (- (* ax by) (* ay bx))]]
    (as-vec r)))

(defn area
  "Return the area expanded by the two vectors."
  [a b]
  (length (cross a b)))

(defn between
  "Return vector between two points"
  [{a :comps} {b :comps}]
  (as-vec (mapv - b a)))

;;; Lines

(defn lwith
  "Find a point by specifying the parameter of the line."
  [t {{op :comps} :op {r :comps} :r}]
  (as-vec (mapv + op (map (partial * t) r))))

;;; Planes & PPlanes

(defn three-points
  "Return three points on a normal plane."
  [{{n :comps} :n d :d}]
  (let [[x y z] (map #(/ (- d) %) n)]
    [[x 0 0]
     [0 y 0]
     [0 0 z]]))

(defn param
  "Return a normal plane in parameter-form"
  [{:keys [n d] :as a}]
  (let [opq (three-points a)
        [o p q] (map (fn [v] (as-vec v)) opq)
        r1 (between o p)
        r2 (between o q)]
    (as-pplane o r1 r2)))

(defn normal
  "Return the same plane as a normal vector and a point."
  [{:keys [op r1 r2 type] :as a}]
  (if (= type :pplane)
    (let [n (cross r1 r2)
          d (reduce - 0 (map * (:comps op) (:comps n)))]
      (as-plane n d))
    a))

(defn pwith
  "Return a point on the plane by specifying the two parameters."
  [s t {:keys [op r1 r2]}]
  (as-vec (mapv + (:comps op)
                (map (partial * s) (:comps r1))
                (map (partial * t) (:comps r2)))))

;;;; Helpers

(defn line
  "Takes two points and returns a line through them."
  [a b]
  (as-line a (between a b)))

(defn plane
  "Returns a normal-plane from a normal vector and a point
   or
   a plane in parameter form from three points."
  ([n p]
     (as-plane n (reduce - 0 (map * (:comps p) (:comps n)))))
  ([A B C]
     (let [op A
           r1 (between A B)
           r2 (between A C)]
       (as-pplane op r1 r2))))

;;;; Multimethods

;;; Mechanics

(defn types
  "Returns a seq of `:type`s to for methods to match on."
  [& xs]
  (map :type xs))

(defn unknown-input
  [args]
  (str "No method found for input types: "
       (apply str (interpose ", " (map name (apply types args))))))

;;; Angle

(defmulti angle
  "Returns the angle between two elements."
  types)

(defmethod angle [:vector :vector]
  [a b]
  (let [[a' b'] (map normalize [a b])
        v (Math/toDegrees (Math/acos (/ (dotp a' b') (* (length a') (length b')))))]
    (if (Double/isNaN v) 0.0 v)))

(defmethod angle [:line :vector]
  [{r :r} a]
  (angle r a))

(defmethod angle [:line :line]
  [{rl :r} {rm :r}]
  (angle rl rm))

(defmethod angle [:plane :vector]
  [{n :n} r]
  (let [v (Math/toDegrees (Math/acos (/ (dotp n r) (* (length n) (length r)))))]
    ((comp first filter) pos? [(- 90 v) (- v 90)])))

(defmethod angle [:plane :plane]
  [{na :n} {nb :n}]
  (let [v (Math/acos (/ (dotp na nb) (* (length na) (length nb))))]
    (Math/toDegrees (min v (- 180 v)))))

(defmethod angle [:line :plane]
  [{r :r} a]
  (angle a r))

(defmethod angle :default
  [& args]
  (unknown-input args))

;;; Parallel

(defn parallel?
  "Returns whether two elements are parallel."
  [& xs]
  (let [v (apply angle xs)]
    (or (= v 0.0) (= v 180.0))))

;;; Perpendicular

(defn perpendicular?
  "Returns whether two elements are perpendicular."
  [& xs]
  (let [v (apply angle xs)]
    (or (= v 90.0) (= v 270.0))))

;;; Distance

(defmulti distance
  "Returns the distance between two elements."
  types)

(defmethod distance [:vector :vector]
  [p q]
  (->> (:comps (between p q))
       (map #(Math/pow % 2))
       (reduce +)
       Math/sqrt))

(defmethod distance [:line :vector]
  [{:keys [r op]} p]
  (/ (length (cross r (between op p)))
     (length r)))

(defmethod distance [:line :line]
  [{rl :r op :op} {rm :r oq :op}]
  (let [n (cross rl rm)
        pq (between op oq)]
    (/ (Math/abs (dotp n pq))
       (length n))))

(defmethod distance [:plane :vector]
  [{n :n d :d} {p :comps}]
  (/ (Math/abs (reduce + d (map * (:comps n) p)))
     (length n)))

(defmethod distance [:line :plane]
  [{op :op :as l} a]
  (if (parallel? l a)
    (distance a op)
    0))

(defmethod distance [:plane :plane]
  [{{[a b c] :comps} :n d :d :as alpha} beta]
  (if (parallel? alpha beta)
    (let [z (/ (- d) c)
          p [0 0 z]]
      (distance beta {:type :vector :comps p}))
    0))

(defmethod distance :default
  [& args]
  (unknown-input args))

;;; On
(def on?
  "Returns whether the element are touching."
  #(= 0.0 (apply distance %&)))

;;; Intersection

(defmulti intersection
  "Returns the intersection point or line between two elements."
  types)

(defmethod intersection [:line :line]
  [{ap :op ar :r :as a} {bp :op br :r}]
  (let [lv3 (between ap bp)
        cab (cross ar br)
        c3b (cross lv3 br)
        planar-factor (dotp lv3 cab)]
    (when (= 0 planar-factor)
      (let [s (/ (dotp c3b cab) (reduce + (map #(* % %) (:comps cab))))]
        (lwith s a)))))

(defmethod intersection [:plane :plane]
  [{{[a1 b1 c1] :comps} :n d1 :d} {{[a2 b2 c2] :comps} :n d2 :d}]
  (let [op [0
            (/ (- (* c1 d2) (* c2 d1)) (- (* b1 c2) (* b2 c1)))
            (/ (- (* b2 d1) (* b1 d2)) (- (* b1 c2) (* b2 c1)))]
        r [1
           (/ (- (* a2 c1) (* a1 c2)) (- (* b1 c2) (* b2 c1)))
           (/ (- (* b2 a1) (* b1 a2)) (- (* b1 c2) (* b2 c1)))]]
    (as-line (as-vec op) (as-vec r))))

(defmethod intersection [:line :plane]
  [{{[opx opy opz] :comps} :op {[rx ry rz] :comps} :r :as l}
   {{[a b c] :comps} :n d :d}]
  (let [t (/ (- (+ (* a opx) (* b opy) (* c opz) d))
             (+ (* a rx) (* b ry) (* c rz)))]
    (lwith t l)))

(defmethod intersection :default
  [& args]
  (unknown-input args))

;;; Projection

(defmulti projection
  "Returns the projection of one element onto the other."
  types)

(defmethod projection [:vector :vector]
  [a b]
  (as-vec (mapv (partial * (/ (dotp a b) (Math/pow (length b) 2))) (:comps b))))

(defmethod projection [:line :plane]
  [{r :r :as l} {n :n :as a}]
  (let [pi (intersection l a)
        rm (mapv (partial - (/ (dotp r n) (Math/pow (length n) 2))) (:comps r))]
    (as-line pi (as-vec rm))))

(defmethod projection :default
  [& args]
  (unknown-input args))

;;;; Skewed lines

(defn skewed?
  "Returns whether two lines are skewed,
   ie. neither parallel nor touching."
  [l m]
  (not (or (parallel? l m) (on? l m))))

;;;; Printing

(defn- sign
  [n]
  (if (neg? n)
    (str n)
    (str "+" n)))

(defmulti mathy
  "Pretty-print math."
  #(get % :type (type %)))

(defmethod mathy java.lang.Double
  [n]
  (.format (NumberFormat/getInstance (Locale/US)) n))

(defmethod mathy clojure.lang.Ratio
  [n]
  (mathy (double n)))

(defmethod mathy :vector
  [{v :comps}]
  (str "(" (apply str (interpose ", " (map mathy v))) ")"))

(defmethod mathy :line
  [{:keys [op r]}]
  (str (mathy op) " + t * " (mathy r)))

(defmethod mathy :plane
  [{{[a b c] :comps} :n d :d}]
  (str a "x" (sign b) "y" (sign c) "z" (sign d) "=0"))

(defmethod mathy :pplane
  [{:keys [op r1 r2]}]
  (str (mathy op) " + s *" (mathy r1) " + t * " (mathy r2)))

(defmethod mathy :default [x] (str x))

;;;; Repl

(defn -main
  [& args]
  (loop []
    (print "> ") (flush)
    (let [input (read-line)
          output (try (parse input)
                      (catch Exception e (str "Wrong input: " (.getMessage e))))]
      (when-not (contains? #{"quit" "exit"} input)
          (println (mathy output))
          (recur)))))
