begin
    comment
        Intended to verify numeric labels and dummy statements;

    integer x;
    x := 0;

    goto 30;

10:
    x := 99;

20:
    ;

30:
    x := x + 1;
    if x < 3 then goto 20;

40:
    outinteger(1, x);
    outstring(1, "\n")
end
