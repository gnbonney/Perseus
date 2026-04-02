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
end;

real procedure sqrt(E);
    value E;
    real E;
begin
    sqrt := sqrt(E)
end;

real procedure sin(E);
    value E;
    real E;
begin
    sin := sin(E)
end;

real procedure cos(E);
    value E;
    real E;
begin
    cos := cos(E)
end;

real procedure arctan(E);
    value E;
    real E;
begin
    arctan := arctan(E)
end;

real procedure ln(E);
    value E;
    real E;
begin
    ln := ln(E)
end;

real procedure exp(E);
    value E;
    real E;
begin
    exp := exp(E)
end
