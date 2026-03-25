begin
    integer procedure Spur(lo, hi);
        value lo, hi;
        integer lo, hi;
    begin
        integer k, s;
        s := 0;
        for k := lo step 1 until hi do
            s := s + a[k, k];
        Spur := s
    end;

    procedure Transpose(lo, hi);
        value lo, hi;
        integer lo, hi;
    begin
        integer i, k, w;
        for i := lo step 1 until hi do
            for k := i + 1 step 1 until hi do
            begin
                w := a[i, k];
                a[i, k] := a[k, i];
                a[k, i] := w
            end
    end;

    integer array a[-1:1, -1:1];

    a[-1, -1] := 1;
    a[-1,  0] := 2;
    a[-1,  1] := 3;
    a[ 0, -1] := 4;
    a[ 0,  0] := 5;
    a[ 0,  1] := 6;
    a[ 1, -1] := 7;
    a[ 1,  0] := 8;
    a[ 1,  1] := 9;

    outinteger(1, Spur(-1, 1));
    outstring(1, "\n");

    Transpose(-1, 1);

    outinteger(1, a[-1, 0]);
    outstring(1, "\n");
    outinteger(1, a[0, -1]);
    outstring(1, "\n");
    outinteger(1, Spur(-1, 1));
    outstring(1, "\n")
end
