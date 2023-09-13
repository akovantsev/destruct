# What

Destructure-first `=>` and destructure-last `=>>` destructuring macros


## Install

```clojure
;; in deps.edn
{:deps {github-akovantsev/destruct
        {:git/url "https://github.com/akovantsev/destruct"
         :sha     ""}}} ;; actual sha
```


## Overview

```clojure
    ;; input:
(=> {:a           1
     "with space" 2
     [1 2]        3
     [2 2]        4
     :e           {:d (range 10)}}

    ;; destructuring form:
    {:a           a    ;; not mirrored {sym :a}, but {:a sym}
     "with space" b    ;;
     [a b]        c    ;; dynamic key constructed from bound syms
     [(inc a) b]  d    ;; arbitrary (I hope :D) inline code with just bound syms 
     :e          {:d  [fst snd | mid | m n]  ;; seq and stack destructuring
                  :x  x                      ;; missing key
                      ;; orp(or-present) defaults using bound syms (more below):
                  :y  (orp y x d)}}            

    ;; body:
    [a b c d, fst snd mid m n, x y])

;;returns:
[1 2 3 4, 0 1 [2 3 4 5 6 7] 8 9, nil 4]
                                    ;^ (or missing missing d)
                                    ;^ a = (get arg :a) = 1
                                    ;^ b = (get arg "with space") = 2
                                    ;^ d = (get arg [(inc a) b]) = 4
```



## Features

### -> ->> Threadable

```clojure
(->   {:a {:b {:c {:d [1 2]}}}}
  (=> {:a {:b {:c {:d [x y]}}}}
    (+ x y)))
;; => 3

(->> (range) (filter even?) (take 5)
  (=>> [head | x]
    {:head head :last x}))
;; => {:head [0 2 4 6] :last 8}
```

### Seq destructuring

Familiar seq destructuring:
```clojure
(=> [1 2 3 4 5] [a b c & tail] [a b c tail])
;; => [1 2 3 (4 5)] ;; tail is seq
```
Vec (stack?) destructuring
```clojure
(=> [1 2 3 4 5] [head | a b c] [head a b c])
;; => [[1 2] 3 4 5] ;; head is vec
```

notice `|` instead of `&`, this is to distinguish between these
(when binding only 2 symbols):
```clojure
(=> [1 2 3 4 5] [a & b] [a b]) ;; => [1 (2 3 4 5)]
(=> [1 2 3 4 5] [a | b] [a b]) ;; => [[1 2 3 4] 5]
```

When destructuring both leading and tailing elements at the same time – use any combination you like:
```clojure
(=> [1 2 3 4 5] [a & b & c] [a b c]) ;; => [1 [2 3 4] 5]
(=> [1 2 3 4 5] [a & b | c] [a b c]) ;; => [1 [2 3 4] 5]
(=> [1 2 3 4 5] [a | b & c] [a b c]) ;; => [1 [2 3 4] 5]
(=> [1 2 3 4 5] [a | b | c] [a b c]) ;; => [1 [2 3 4] 5]
```
There is only 1 "binding slot" between the `| &` - middle.

Middle is `vec`, unless destructuring form is longer than input – then middle is `nil`. 

```clojure
; for this illustration I use destructure-last macro =>>
;    destructuring:     body:          input:          returns:
(=>> [a b | mid | c d]  [a b mid c d]  [1 2 3 4 5 6]) ;; [1   2 [3 4] 5   6]
(=>> [a b | mid | c d]  [a b mid c d]  [1 2 3 4 5])   ;; [1   2   [3] 4   5]
(=>> [a b | mid | c d]  [a b mid c d]  [1 2 3 4])     ;; [1   2   []  3   4]
(=>> [a b | mid | c d]  [a b mid c d]  [1 2 3])       ;; [1   2   nil 2   3]
(=>> [a b | mid | c d]  [a b mid c d]  [1 2])         ;; [1   2   nil 1   2]
(=>> [a b | mid | c d]  [a b mid c d]  [1])           ;; [1   nil nil nil 1]
(=>> [a b | mid | c d]  [a b mid c d]  [])            ;; [nil nil nil nil nil]
(=>> [a b | mid | c d]  [a b mid c d]  nil)           ;; [nil nil nil nil nil]
```

Full seq+vec destructuring form `[a b | mid | c d]` conceptually has 6 parts,
which we would like to name:
1. mid
2. individual leading elements
3. individual trailing elements
4. entire form
5. tail (everything after first `|`)
6. head (everything until second `|`)

This destructuring does not support `:as` as core destructuring does.<br>
Instead, it abuses `:tag` meta which has reader shorthand `^`:
```clojure
(let [foo 'foo]
  (meta ^foo [1 2 3]))
;; {:tag foo}
```

So you have to (sorry):
- add `^sym` to vec form, to name entire form:
  ```clojure
  (=> [1 2 3 4 5 6]
   ^all [a b | mid | c d]
   [a b mid   c d all])
  ;[1 2 [3 4] 5 6 [1 2 3 4 5 6]]
  ```
- add `^sym` to **first** separator to name **tail**:
  ```clojure
  (=> [1 2 3 4 5 6]
   [a b ^tail | mid | c d]
   [a b mid   c d tail])
  ;[1 2 [3 4] 5 6 (3 4 5 6)]
  ```
- add `^sym` to **second** separator to name **head**:
  ```clojure
  (=> [1 2 3 4 5 6]
   [a b | mid ^head | c d]
   [a b mid   c d head])
  ;[1 2 [3 4] 5 6 [1 2 3 4]]
  ```

Seq destructuring can be nested:
```clojure
(=> [1 2 3 4 5 6]
  [a b | ^mid [f | g | h] | c d]
  [a b mid   c d, f g  h])
 ;[1 2 [3 4] 5 6, 3 [] 4]
```

### Map destructuring

**The most important feature**:<br>
it looks just like input map: no inversion, no funky mirroring.
Compare these:
```clojure
(let [t     (:com.my-funky-service.core/type input)
      attrs (get-in m [:com.my-funky-service.foobar/attributes-by-type t])])
```

```clojure
{:com.my-funky-service.core/entity-type          t
 :com.my-funky-service.foobar/attributes-by-type {t attrs}}
```

No shortcuts: you have to spell out keys (except aliasing, ofc).
<br>This means you are not tempted to introduce short simple
keys **just** because it would make destructuring sweeter (like core's `:keys`).
<br>This also means there are no generated syms (`{:keys [:a]}` gens `a`),
no implicit keys mentions (`{:keys [a]}` uses `:a` w/o "mentioning" it),
so source code will contain both `key` and `sym` verbatim, no IDE support needed (though, I am sorry if you **have to** rely on raw text search only). 

There are conceptually 2 slots: `key, val`.

```clojure
;   input:         destructuring:        body:
(=> {:k "a" :a 1}  {:k k (keyword k) v}  [k v]) ;; ["a" 1]
;            binding:  ^             ^
;            using:               ^       ^ ^
```

`Key` slot can contain arbitraty code, and use bound syms, as `(keyword k)` fn call above

`Val` slot can contain only:
- symbols, interpreted as new sym bind
- vectors, interpreted as seq destructuring
- maps, interpreted as map destructuring
- `ort ors orp` call (see `Defaults` below).

To bind entire map to `sym` - add meta to map destructuring form, just like for seq destructuring:
```clojure
(=> {:a {:b 1}}
    {:a ^m
        {:b b}}
    [b m])
;   [1 {:b 1}]
```

### Defaults

Neither you controll all the inputs, nor discipline scales, so you basically can't rely on core's `:or`, which applies only when key is **missing**.

To avoid resorting to pre- and post-processing, there are 3 or-like functions available inside `=>` and `=>>`:

<table>
<tr><th>description<th>fn<td>missing<td>nil<td>false<td>:foo<td>
<tr><td>"or present"<th> orp <td>-<td>+<td>+<td>+<td>returns first present arg or last arg val
<tr><td>"or some"   <th> ors <td>-<td>-<td>+<td>+<td>returns first some? arg or last arg val
<tr><td>"or truthy" <th> ort <td>-<td>-<td>-<td>+<td>returns first truthy arg or last arg val
</table>

```clojure
{:foo A
 :bar B
 :baz (ors C B A :foo)}
      ;      ^^^^^^^^^ arbitrary code; here, if :baz key's value is absent or nil - 
      ;      it falls back to B and A bound above, and then to keyword :foo
      ;    ^ sym name, result will be bound too
      ;^ one of 3 function names: orp ors ort
```

`missing` is visible only within destructuring form **outside** of arbitrary code slots (`key` slot).<br>
`missing` is "rendered" as `nil` in all `key` slots and in `=>`'s `body`.

`orp ort ors` are supported in seq destructuring too:
```clojure
(=> [1]       [a b]         [a b])      ; [1 nil]
(=> [1 nil]   [a (orp b a)] [a b])      ; [1 nil]   ;;or present?
(=> [1 nil]   [a (ors b a)] [a b])      ; [1 1]     ;;or some?
(=> [1 false] [a (ors b a)] [a b])      ; [1 false] ;;or some?
(=> [1 false] [a (ort b a)] [a b])      ; [1 1]     ;;or truthy?

(=> [nil false] [a (ort b a :c)] [a b])   ; [nil :c]

(let [x false]
  (=> [nil false] [a (ort b a x :c)]  ;;or truthy?
    [a b])) ; [nil :c]
```

`orp ort ors` don't work in shorthand meta `^sym`:
```clojure
(=> {:a [1 2]}
    {:a a
     :b ^(orp b a)
        [x y]}
    [a b x y])
; Syntax error reading source at (REPL:4:17).
; Metadata must be Symbol,Keyword,String or Map
```

but works (I guess?) in `^{:tag ...}`:
```clojure
(=> {:a [1 2]}
    {:a a 
     :b ^{:tag (orp b a)}
        [x y]}
    [a     b     x y])
;   [[1 2] [1 2] 1 2]
```

### Body

While we are inside a macro, why not get rid of more pre-/post-processing?

Don't you hate it when you need to thread big-ass map through `remove-nils` or `assoc-some`, etc. functions?<br>
Huge diff, indentation level change, let bindings, ugh.<br>
This is one of the reasons why "discipline does not scale", and you can't rely on core's destructuring `:or`.

```clojure
(=> {:a nil} {:a a} {:add-to-map-only-if-not-nil?    ^? a}) ; nil
(=> {:a nil} {:a a} {:add-to-map-only-if-not-empty? ^?? a}) ; nil

(=> {:a [1 2 3]} {:a a} {:not-ints ^?? (remove int? a)})    ; nil
;; ^? ^?? can be inside val to exclude entire val based on some subform:
(=> {:a [1 2 3]} {:a a} {:not-ints (remove int? ^?? a)})    ; {:not-ints ()}
(=> {:a nil}     {:a a} {:not-ints (remove int? ^?? a)})    ; nil

;; ^? ^?? context border is at the map level, so multiple nested maps need multiple ^?:
(=> {:a nil} {:a a} {:lvl-1    {:lvl-2 ^? a}})       ; {:lvl-1 nil}
(=> {:a nil} {:a a} {:lvl-1 ^? {:lvl-2 ^? a}})       ; nil
(=> {:a nil} {:a a} {:lvl-1 ^? {:lvl-2 ^? a :b 1}})  ; {:lvl-1 {:b 1}}
 
```

## Why

If it is not obvious from the features section – cue the rant!

### Mental gymnastics

Quick, bind `a` to `"a"` and `c` to `"c"` from `m`:
```clojure
{:a "a"
 :b {:c "c"}}
```
Performance conciderations aside, what did you write?
```clojure
;; this?
(let [a (-> m :a)
      c (-> m :b :c)])

;; or maybe:
(let [{:keys [a b]
       {:keys [c]} b} m])

;; or:
(let [{:keys [a b]
       {c :c} b} m])

;; or:
(let [{a :a {c :c} :b} m])
```
Does any of these resembles original `m`? What, you can't read maps backwards? Pfft...

```clojure
;; Let's make best lookalike a bit less inliney:
(let [{a :a
       {c :c} :b} m])

;; Hm. KINDA.
;; I forgot we need b too:
(let [{a :a
       {c :c 
        :as b} :b} m])

;; Surely alignment must help:
(let [{a       :a
       {c   :c
        :as b} :b} m])

;; :)
```

But just to drive it home, here is the example from https://clojure.org/guides/destructuring

```clojure
(defn print-contact-info
  [{:keys [f-name l-name phone company title]
    {:keys [street city state zip]} :address
    [fav-hobby second-hobby] :hobbies}]
  (println f-name l-name "is the" title "at" company))

;; aligned
(defn print-contact-info
  [{:keys                           [f-name l-name phone company title]
    {:keys [street city state zip]} :address
    [fav-hobby second-hobby]        :hobbies}]
  (println f-name l-name "is the" title "at" company))

;; :D
;; forgot to get something from address:
(defn print-contact-info
  [{:keys                                    [f-name l-name phone company title]
    {:keys             [street city state zip district-id access-code]
     fedex-district-id :com.fedex/district-id
     ups-district-id   :com.ups/district-id} :address
    [fav-hobby second-hobby]                 :hobbies}]
  (println f-name l-name "is the" title "at" company))

;; :DD 
;; Dont you love it when key hangs over the value.
;; or maybe?

(defn print-contact-info
  [{:keys                                                               [f-name l-name phone company title]
    {fedex-district-id :com.fedex/district-id
     usps-district-id  :com.ups/district-id
     :keys             [street city state zip district-id access-code]} :address
    [fav-hobby second-hobby]                                            :hobbies}]
  (println f-name l-name "is the" title "at" company))

;; :DDD
;; Have you got horisontal scroll already, or not yet?
;; dude reading the diff would not be trilled.
```


After 9 years, I still cant write one from the top of my head. But that's ok.
What is not ok is that I still have hard time reading one.
Editing difficulty depends on the particular aspect I need to change (more on that later).

But what if destructuring would look like map it destructures?
```clojure
{:f-name  f-name
 :l-name  l-name
 :phone   phone
 :company company
 :title   title
 :address {:com.fedex/district-id fedex-district-id
           :com.ups/district-id   ups-district-id
           :street                street
           :city                  city
           :state                 state
           :zip                   zip
           :district-id           district-id
           :access-code           access-code}
 :hobbies [fav-hobby second-hobby]}
```
As a side effect it screams at you
`your short keys suck! the fuck is company? f-name? f-you!`<br>
ok ok, not `it`, it is me, I scream at you.

So what?

#### So, reason #1: Prosopagnosia

Core map destructuring forms do not look like maps they are destructuring.

Having structures as keys sucks. Having nested structures as keys sucks exponentially more.
It is unusual for the eye to read, and hense: longer to comprehand, easier to make a mistake or misread.

Due to code being linear, destructuring maps are not vertical reflections of input structure,
but each kv is diagonally reflected. Such reflection does not compose into "entire map is diagonally reflected":

<table>
<tr>
<td>

```clojure
{:foo {:lucy  lucy
       :mary  mary
       :garry garry}
 :bar {:alice-alice-alice alice
       :kelly             kelly
       :bob               bob}
 :baz {:peter peter
       :sean  sean
       :jeff  jeff}}
```

</td>
<td>

```clojure
{{lucy  :lucy
  mary  :mary
  garry :garry} :foo
 {alice :alice-alice-alice
  kelly :kelly
  bob   :bob}   :bar
 {peter :peter
  sean  :sean
  jeff  :jeff}  :baz}
```

</td>
</table>

Paths to the "same" slots are different in a funky way, instead of being identical.

Vertical alignment in presense of funky keys is visually different, which inhibits recognition.


#### Reason #2: core map destructuring encourages use of simple keywords.

Unqualified keywords are convenient. But pollute "autosuggests space", and inhibit discoverability.
Autosuggests in IDEs have 2 useful aspects:
- autocomplete: 
  - reduces keystrokes (speed, convenience, fewer typos, frustration).
- discoverability (recollection): 
  - list ns symbols w/o switching files,
  - list possible enum values!

Having most of keywords in a project - unqualified is both discoverability and typos hell,
because all of those now belong to a single enum: GLOBAL

Hello all the: ```:key :val :value :method :id :name :type :get :GET :post :POST :status :body :code :error```.
Good luck finding relevant usages or renaming any of those without looking at all the 666999 false positives.

(Should be a separate rant, but this why honey1's column name aliasing is a fucking disaster too, as if SQL was not painful enough already).

SO. Reason #1 makes `{:keys []}` form default choice for map destructuring, 
and you can't `{:keys [:foo/id :bar/id]}` (you can, BUT, hehehe),
so having qualified keywords pushes you to harder to read destructuring, making it even more inconvenient.

`Discipline does not scale.`

#### Reason #3: core map destructuring :or

`:or` does not mean that `clojure.core/or` means. <br>
It means "if key is not present, use this value". <br>
It's ok, but it is a "name reuse", so you have to remember what **this one** does.

As you might recall: `Discipline does not scale`,
and you are gonna get nils in maps where key had to be ommitted instead,
which renders `:or` at the very least unreliable.
To avoid feeling of unease, you just manually compare and redef values in `let` after destructuring.

Also, writing symbol twice? Pffft...


#### Reason #4: core map destructuring requires non-trivial IDE support

`{:keys []}` breaks full text search of keywords OR
an IDE must know that keyword (string) in some slot of destructuring form defines `(name keyword)` symbol.
`{:foo/keys []}` does the same, but adds to `:foo/` virtual (autosuggest) enum 1 more key,
which has nothing to do with problem domain.

For more examples, see Cursive's issues, e.g. https://github.com/cursive-ide/cursive/issues/2629

#### Reason #5: can't use bound sym in the same destructuring form
Didn't you ever wanted to?
```clojure
(let [{k :k v k} {:k :a :a 1 :b 2}]
  [k v])
;=> [:a 1]
```

#### Reason #6: seq destructuring can't peek

I like seq destructuring! It looks like input.
But it does not support stack(?) destructuring, which is a bummer.

Similar kind of bummer as
```clojure
(pop [])
Execution error (IllegalStateException)
Can't pop empty vector
```

#### Reason #7: seq destructuring has no :or 

So you can't tell whether seq contained nil or was shorter than you expected:
```clojure
(let [[a b] [1 nil]] [a b]) ;; [1 nil]
(let [[a b] [1]]     [a b]) ;; [1 nil]
```
And either need extra code outside, or rewrite w/o destructuring altogether.

#### Reason #8: any -> ->> threading into destructuring looks cursed

It is pretty much "stop the flow! I am destructuring!".
It should not be that special.