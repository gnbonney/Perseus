Pages 55-56 from Dijkstra's PROGRAMMING PRIMER FOR ALGOL 60, 1962

Editorial note: the original printed text uses a distinct integer-division symbol.
This transcription renders it as ÷ for readability.

**19. The Value List**

The procedures described thus far are correct—at least, we hope that they are—but they are not quite realistic. Returning to the last but one example, let us investigate the implications of the function call in

```
‘k := MOD(n × (2 × n + 1), m × (m + 7) − 3, m is wrong)’.
```

This call defines the formal parameters p and q by fairly complicated expressions; if we inspect the text of the procedure *MOD*, then we see that as a rule the value of p is needed twice and that of q three or four times. But in the execution of the statement given above, this implies that the first actual parameter is evaluated twice and the second one three or four times. Obviously it is not our intention to keep the machine occupied by repeated evaluation of the same expressions and in this respect the function procedure *MOD* is not realistic.

The difficulty can be overcome by introducing two extra local variables

```
‘integer procedure MOD (p, q, L); integer p, q; label L;
begin integer s, ploc, qloc;
  ploc := p; qloc := q;
  if qloc = 0 then goto L;
  s := ploc − ploc ÷ qloc × qloc;
  if s < 0 then MOD := s + abs (qloc) else MOD := s
end’.
```

The need for such a ‘recoding’ arises so often that a special mechanism for abbreviation has been introduced for this purpose: the specifications are preceded by the symbol **value**, followed by a list of formal parameter identifiers. By inserting such a ‘value list’, we can now give a shorter form of the improved version of *MOD*, viz.

```
‘integer procedure MOD (p, q, L); value p, q; integer p, q; label L;
begin integer s;
  if q = 0 then goto L;
  s := p − p ÷ q × q;
  if s < 0 then MOD := s + abs (q) else MOD := s
end’.
```

The effect of the *value list* can also be described as follows. For all formal parameters occurring in the value list, the corresponding actual parameter is calculated once on entry into the procedure body, and the values thus obtained are assigned to the formal parameter, which will subsequently be treated as a normal local variable of the body. We should like to stress that the introduction of the value list did not essentially enrich the expressive power of the language. We can describe the effect with the rest of ALGOL 60 as well. In the last definition the fact that we then need new identifiers (e.g. ‘ploc’ and ‘qloc’), has been pushed somewhat into the background.

Besides scalar formal parameters, the value list may also contain formal parameters specified to represent **array identifiers**. If a formal parameter is specified to be an array, then the corresponding actual parameter may only be an array identifier referring to an array of the correct dimensions. If this formal parameter occurs in the value list, a local array is introduced with the same number of subscripts and the same subscript bounds as the corresponding actual array. Subsequently, the outside array is copied into the local one element by element. The presence of a formal array identifier in the value list may, therefore, cause a considerable amount of work when the procedure is called.
N.B. For the MC translator the maximum number of dimensions of a value array is five. We do not expect this somewhat curious restriction to have any serious consequences for the user.

We leave the verification of the following consequences to the reader. If a formal parameter, specified to represent a scalar, occurs in the procedure body to the left of the becomes sign of an assignment statement, then a number or an expression (such as ‘15’, ‘+a’, ‘b/c’ or ‘(d)’ etc.) is not allowed to act as the corresponding actual parameter, except when the formal parameter in question occurs in the value list.


