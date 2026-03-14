begin
    integer k;

    procedure outer;
    begin
        k := 1;
        procedure inner;
        begin
            k := k + 1
        end;
        inner;
        outinteger(1, k); (* EXPECT: 2 *)
        outstring(1, "\n");
    end;

    outer
end
