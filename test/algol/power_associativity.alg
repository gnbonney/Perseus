begin
    comment
        Adapted from the Modified Report discussion of exponentiation.
        Intended to verify ** and ^, including associativity;

    integer n, k, m;
    n := 3;
    k := 2;
    m := 2;

    outinteger(1, 2 ** n ** k);
    outstring(1, "\n");
    outinteger(1, 2 ** (n ** m));
    outstring(1, "\n");
    outinteger(1, 2 ^ 3);
    outstring(1, "\n")
end
