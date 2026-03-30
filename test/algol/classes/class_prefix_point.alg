comment Draft Milestone 33 sample: Simula-style prefix inheritance;
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

    Point class ColoredPoint(x0, y0, shade);
        value shade;
        string shade;
    begin
        string color;

        color := shade
    end;

    ref(ColoredPoint) p;

    p := new ColoredPoint(3, 4, "red");
    p.move(1, 2);
    outreal(1, p.length)
end
