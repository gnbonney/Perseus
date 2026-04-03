comment Compiled standard-environment slice for basic numeric helpers.;
namespace perseus.lang;

external java static(java.lang.Math)
    integer procedure abs(integer x) as math_iabs;
external java static(java.lang.Math)
    real procedure abs(real x) as math_abs;
external java static(java.lang.Math)
    real procedure floor(real x) as math_floor;
external java static(java.lang.Math)
    real procedure sqrt(real x) as math_sqrt;
external java static(java.lang.Math)
    real procedure sin(real x) as math_sin;
external java static(java.lang.Math)
    real procedure cos(real x) as math_cos;
external java static(java.lang.Math)
    real procedure atan(real x) as math_atan;
external java static(java.lang.Math)
    real procedure log(real x) as math_log;
external java static(java.lang.Math)
    real procedure exp(real x) as math_exp;
external java static(gnb.perseus.runtime.MathConstantsSupport)
    real procedure maxreal as runtime_maxreal;
external java static(gnb.perseus.runtime.MathConstantsSupport)
    real procedure minreal as runtime_minreal;
external java static(gnb.perseus.runtime.MathConstantsSupport)
    integer procedure maxint as runtime_maxint;
external java static(gnb.perseus.runtime.MathConstantsSupport)
    real procedure epsilon as runtime_epsilon;

real procedure abs(E);
    value E;
    real E;
begin
    abs := math_abs(E)
end;

integer procedure iabs(E);
    value E;
    integer E;
begin
    iabs := math_iabs(E)
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
    sqrt := math_sqrt(E)
end;

real procedure sin(E);
    value E;
    real E;
begin
    sin := math_sin(E)
end;

real procedure cos(E);
    value E;
    real E;
begin
    cos := math_cos(E)
end;

real procedure arctan(E);
    value E;
    real E;
begin
    arctan := math_atan(E)
end;

real procedure ln(E);
    value E;
    real E;
begin
    ln := math_log(E)
end;

real procedure exp(E);
    value E;
    real E;
begin
    exp := math_exp(E)
end;

integer procedure entier(E);
    value E;
    real E;
begin
    entier := math_floor(E)
end;

real procedure maxreal;
begin
    maxreal := runtime_maxreal()
end;

real procedure minreal;
begin
    minreal := runtime_minreal()
end;

integer procedure maxint;
begin
    maxint := runtime_maxint()
end;

real procedure epsilon;
begin
    epsilon := runtime_epsilon()
end
