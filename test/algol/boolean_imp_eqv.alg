begin
    comment
        Adapted from Algol Boolean-expression material in the Revised and Modified Reports.
        Intended to verify imp and eqv plus precedence with not, and, and or;

    boolean p, q, r;
    p := true;
    q := false;
    r := true;

    if p imp q then outstring(1, "bad-imp-1\n") else outstring(1, "imp-1\n");
    if q imp p then outstring(1, "imp-2\n") else outstring(1, "bad-imp-2\n");
    if p eqv r then outstring(1, "eqv-1\n") else outstring(1, "bad-eqv-1\n");
    if p eqv q then outstring(1, "bad-eqv-2\n") else outstring(1, "eqv-2\n");

    if not p or q imp r then outstring(1, "precedence-1\n") else outstring(1, "bad-precedence-1\n");
    if p and r eqv q or false then outstring(1, "bad-precedence-2\n") else outstring(1, "precedence-2\n")
end
