begin
    external java class java.awt.Point;

    ref(Point) p;

    p := new Point(3, 4);

    outinteger(1, p.x);
    outstring(1, ",");
    outinteger(1, p.y)
end
