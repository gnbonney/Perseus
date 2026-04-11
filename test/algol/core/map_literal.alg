begin
    map string real scores;

    scores := map("Alice": 95.5, "Bob": 87.0);

    outinteger(1, length(scores));
    outterminator(1);
    outreal(1, scores["Alice"]);
    outterminator(1);
    outreal(1, scores["Bob"])
end
