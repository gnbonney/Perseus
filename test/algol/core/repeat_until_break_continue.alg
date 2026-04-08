begin
    comment Milestone 41.1 repeat until with break and continue.;
    integer i, sum;
    i := 0;
    sum := 0;

    repeat
    begin
        i := i + 1;
        if i = 2 then continue;
        if i = 5 then break;
        sum := sum + i
    end
    until i = 10;

    outinteger(1, sum)
end
