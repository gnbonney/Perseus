# ALGOL-60 Example Procedures (from Dijkstra's PROGRAMMING PRIMER FOR ALGOL 60, 1962, APPENDIX 5.4.2, pp. 103-104)

These famous ALGOL-60 examples demonstrate:

* matrix trace
* matrix transpose
* logical function
* search with multiple output parameters
* dot product
* exotic procedure headings
* call-by-name vs call-by-value
* array parameter semantics

## **1. Spur**

```algol
procedure Spur (a) Order (n) Result (s); value n;
array a; integer n; real s;

begin
    integer k;
    s := 0;
    for k := 1 step 1 until n do
        s := s + a[k, k]
end
```

---

## **2. Transpose**

```algol
procedure Transpose (a) Order (n); value n;
array a; integer n;

begin
    real w; integer i, k;
    for i := 1 step 1 until n do
        for k := i + 1 step 1 until n do
        begin
            w := a[i, k];
            a[i, k] := a[k, i];
            a[k, i] := w
        end
end Transpose
```

---

## **3. Step**

```algol
integer procedure Step (u); real u;
    Step := if 0 ≤ u ∧ u ≤ 1 then 1 else 0
```

---

## **4. Absmax**

```algol
procedure Absmax (a) size (n, m) Result (j) Subscripts (k, l);
comment The absolute greatest element of the matrix a, of size n by m,
        is transferred to j, and the subscripts of this element to k and l;

array a; integer n, m, k, l; real j;

begin
    integer p, q;
    j := 0;
    for p := 1 step 1 until n do
        for q := 1 step 1 until m do
            if abs(a[p, q]) > j then
            begin
                j := abs(a[p, q]);
                k := p;
                l := q
            end
end Absmax
```

---

## **5. Innerproduct**

```algol
procedure Innerproduct (a, b) Order (k, p) Result (j); value k;
integer k, p; real j, a, b;

begin
    real s;
    s := 0;
    for p := 1 step 1 until k do
        s := s + a × b;
    j := s
end Innerproduct
```
