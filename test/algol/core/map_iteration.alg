begin
    map string integer counts;
    string key;
    integer entryvalue, total;

    counts := map("alpha": 2, "beta": 4);
    total := 0;

    for key in counts.keys() do
        outstring(1, key);

    for entryvalue in counts.values() do
        total := total + entryvalue;

    outterminator(1);
    outinteger(1, total)
end
