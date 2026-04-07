begin
    comment Milestone 41 first slice: loop, break, and continue.;
    integer i, sum;
    i := 0;
    sum := 0;

    loop
    begin
        i := i + 1;
        if i = 3 then continue;
        if i = 6 then break;
        sum := sum + i
    end loop;

    outinteger(1, sum)
end
