comment Draft Milestone 27 sample: real-valued fields, method mutation, and dotted access;
begin
    class Point(x0, y0);
        value x0, y0;
        real x0, y0;
    begin
        real x, y;

        x := x0;
        y := y0;

        procedure move(dx, dy);
            value dx, dy;
            real dx, dy;
        begin
            x := x + dx;
            y := y + dy
        end;

        real procedure length;
            length := sqrt(x * x + y * y)
    end;

    ref(Point) p;

    p := new Point(3, 4);
    outreal(1, p.length);
    outterminator(1);
    p.move(1, 2);
    outreal(1, p.length)
end
