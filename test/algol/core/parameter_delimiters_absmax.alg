begin
    comment
        Adapted from the classic Absmax example with named parameter delimiters.
        This version stays one-dimensional so the test targets delimiter syntax rather than
        multidimensional formal array parameters;

    procedure Absmax(a) Size:(n) Result:(y) Subscript:(i);
        value n;
        array a;
        integer n, i;
        real y;
    begin
        integer p;
        y := 0;
        for p := 1 step 1 until n do
            if abs(a[p]) > y then
            begin
                y := abs(a[p]);
                i := p
            end
    end;

    real array M[1:6];
    real best;
    integer idx;

    M[1] := -1.5;
    M[2] := 2.0;
    M[3] := -4.5;
    M[4] := 3.0;
    M[5] := -2.5;
    M[6] := 1.0;

    Absmax(M) Size:(6) Result:(best) Subscript:(idx);

    outreal(1, best);
    outstring(1, "\n");
    outinteger(1, idx);
    outstring(1, "\n")
end
