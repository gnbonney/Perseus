comment Draft Milestone 33 sample: multiple reusable classes sharing one namespace;
namespace mylib.geometry;

class Point;
begin
    real x, y;

    procedure set(nx, ny);
        value nx, ny;
        real nx, ny;
    begin
        x := nx;
        y := ny
    end
end;

Point class ColoredPoint(color);
    value color;
    integer color;
begin
    integer procedure getColor;
    begin
        getColor := color
    end
end
