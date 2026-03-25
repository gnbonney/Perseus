begin
    real procedure sum(a, lo, hi);
        value lo, hi;
        array a;
        integer lo, hi;
    begin
        integer i;
        real s;
        s := 0;
        for i := lo step 1 until hi do
            s := s + a[i];
        sum := s
    end;

    procedure shift(a);
        array a;
    begin
        integer i;
        for i := -2 step 1 until 2 do
            a[i] := a[i] + 1
    end;

    real array q[-2:2];
    integer i;

    for i := -2 step 1 until 2 do
        q[i] := i;

    shift(q);
    outreal(1, sum(q, -2, 2));
    outstring(1, "\n")
end
