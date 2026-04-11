begin
    map string real scores;
    real removed;
    boolean hasalice;

    scores["Alice"] := 95.5;
    scores["Bob"] := 87.0;
    hasalice := scores.contains("Alice");
    removed := scores.remove("Bob");

    outreal(1, scores["Alice"]);
    outterminator(1);
    outreal(1, removed);
    outterminator(1);
    outinteger(1, length(scores));
    outterminator(1);
    if hasalice then
        outinteger(1, 1)
    else
        outinteger(1, 0);
    outterminator(1);

    scores.clear();
    outinteger(1, scores.size())
end
