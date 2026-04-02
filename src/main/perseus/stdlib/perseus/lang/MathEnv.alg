comment Compiled standard-environment slice for basic numeric helpers.;
namespace perseus.lang;

real procedure abs(E);
    value E;
    real E;
begin
    abs := if E >= 0.0 then E else -E
end;

integer procedure sign(E);
    value E;
    real E;
begin
    sign := if E > 0.0 then 1
        else if E < 0.0 then -1 else 0
end
