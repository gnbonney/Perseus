begin
    comment Milestone 41.1 while true with break and continue.;
    integer i, sum;
    i := 0;
    sum := 0;

    while true do
    begin
        i := i + 1;
        if i = 3 then continue;
        if i = 6 then break;
        sum := sum + i
    end;

    outinteger(1, sum)
end
