comment Exercise the narrow coercion rule for typed procedure return assignment.;
external java static(java.lang.Math)
    real procedure floor(real x) as math_floor;
begin
    integer procedure floor_to_int(x);
        value x;
        real x;
    begin
        floor_to_int := math_floor(x)
    end;

    outinteger(1, floor_to_int(3.7));
    outstring(1, ",");
    outinteger(1, floor_to_int(0.0 - 2.3))
end
