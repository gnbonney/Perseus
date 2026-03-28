comment Draft Milestone 27 sample: basic class, object creation, and instance procedure calls;
begin
    class Counter(start);
        value start;
        integer start;
    begin
        integer n;

        n := start;

        procedure increment(delta);
            value delta;
            integer delta;
        begin
            n := n + delta
        end;

        integer procedure current;
            current := n
    end;

    ref(Counter) c;

    c := new Counter(5);
    c.increment(2);
    c.increment(3);
    outinteger(1, c.current)
end
