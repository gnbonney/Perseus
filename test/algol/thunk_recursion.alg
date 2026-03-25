begin
    integer k;

    procedure A;
      begin
        k := k + 1;
        if k < 4 then B(A)
      end
    ;

    procedure B(p);
      begin
        p;  % call-by-name recursion
      end
    ;

    k := 0;
    A;
    outinteger(1, k); % EXPECT: 4
    outstring(1, "\n");
end
