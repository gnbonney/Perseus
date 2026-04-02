comment External java static interop sample with a local alias.;
begin
    external java static(java.lang.Math)
        real procedure cos(real x) as math_cos;

    outreal(1, math_cos(0.0))
end
