comment Draft Milestone 27 sample: distinct object state across multiple instances;
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

    ref(Counter) left, right;

    left := new Counter(1);
    right := new Counter(10);

    left.increment(4);
    right.increment(7);

    outinteger(1, left.current);
    outterminator(1);
    outinteger(1, right.current)
end
