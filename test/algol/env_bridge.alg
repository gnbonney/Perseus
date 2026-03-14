begin
    integer k;

    procedure outer;
      begin
        k := 1;
        procedure inner;
          k := k + 1
        ;
        inner;
        outinteger(1, k); (* EXPECT: 2 *)
        outstring(1, "\n");
      end
    ;

    outer
end
