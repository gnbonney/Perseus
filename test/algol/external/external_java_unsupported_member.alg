begin
    external java class java.awt.Point;

    ref(Point) p;
    p := new Point(1, 2);

    p.move("x", "y")
end
