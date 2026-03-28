begin
    integer x;

    procedure inc(n);
    begin
        n := n + 1;
    end;

    procedure twoIncs(a, b);
    begin
        a := a + 1;
        b := b + 1;
    end;

    x := 0;
    twoIncs(x, x);    comment if shared, x would become 3 correct should be 2;
    outinteger(1, x);
    outstring(1, "\n");
end
