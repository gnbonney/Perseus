begin
    comment
        Perseus-specific hardware-representation regression.
        Intended to verify =>, ==, and ^ as synonyms;

    boolean p, q;
    p := true;
    q := false;

    if p => q then outstring(1, "bad-imp-synonym\n") else outstring(1, "imp-synonym\n");
    if p == q then outstring(1, "bad-eqv-synonym\n") else outstring(1, "eqv-synonym\n");
    outinteger(1, 2 ^ 4);
    outstring(1, "\n")
end
