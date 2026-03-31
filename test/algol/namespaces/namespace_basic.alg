comment Draft Milestone 33 sample: a reusable class library with a namespace declaration;
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
    end;

    real procedure length;
    begin
        length := sqrt(x * x + y * y)
    end
end
