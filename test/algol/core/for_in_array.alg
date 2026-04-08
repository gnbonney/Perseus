begin
    comment Milestone 41 array iteration sample.;
    integer array a[2:4];
    integer item, sum, count;

    a[2] := 1;
    a[3] := 2;
    a[4] := 3;

    sum := 0;
    count := 0;

    for item in a do
    begin
        count := count + 1;
        if count = 1 then item := 99;
        sum := sum + item
    end;

    outinteger(1, sum);
    outstring(1, "\n");
    outinteger(1, count)
end
