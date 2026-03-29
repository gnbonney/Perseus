comment Separately compiled Perseus library for external procedure tests;
begin
    real procedure hypot2(a, b);
        value a, b;
        real a, b;
        hypot2 := sqrt(a * a + b * b)
end
