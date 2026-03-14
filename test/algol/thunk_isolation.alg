begin
    integer x;

    procedure inc(n);
        n := n + 1
    ;

    procedure twoIncs(a, b);
        a := a + 1;
        b := b + 1
    ;

    x := 0;
    twoIncs(x, x);    (* if shared, x would become 3; correct should be 2 *)
    outinteger(1, x);
    outstring(1, "\n");
end
