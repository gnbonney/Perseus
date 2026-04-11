begin
    set integer values;
    integer item, total;
    boolean hasfour, removedsix;

    values.insert(2);
    values.insert(4);
    values.insert(6);
    values.insert(4);

    hasfour := values.contains(4);
    removedsix := values.remove(6);
    total := 0;

    for item in values do
        total := total + item;

    outinteger(1, length(values));
    outterminator(1);
    if hasfour then
        outinteger(1, 1)
    else
        outinteger(1, 0);
    outterminator(1);
    if removedsix then
        outinteger(1, 1)
    else
        outinteger(1, 0);
    outterminator(1);
    outinteger(1, total);
    outterminator(1);

    values.clear();
    outinteger(1, values.size())
end
