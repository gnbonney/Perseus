comment Draft Milestone 33 sample: separately compiled library unit with a namespace;
namespace mylib.numeric;

real procedure hypot2(a, b);
    value a, b;
    real a, b;
begin
    hypot2 := sqrt(a * a + b * b)
end
