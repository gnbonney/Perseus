begin
    set integer values;
    integer item, total;

    values := {2, 4, 2, 6};
    total := 0;

    for item in values do
        total := total + item;

    outinteger(1, length(values));
    outterminator(1);
    if values.contains(4) then
        outinteger(1, 1)
    else
        outinteger(1, 0);
    outterminator(1);
    outinteger(1, total)
end
