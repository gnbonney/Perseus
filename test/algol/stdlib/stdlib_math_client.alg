comment Use the compiled Perseus standard environment through builtin math names.;
begin
   outinteger(1, sign(-4.25));
   outstring(1, ",");
   outreal(1, abs(-2.5));
   outstring(1, ",");
   outinteger(1, iabs(0 - 42));
   outstring(1, ",");
   outreal(1, sqrt(9.0));
   outstring(1, ",");
   outreal(1, sin(0.0));
   outstring(1, ",");
   outreal(1, cos(0.0));
   outstring(1, ",");
   outreal(1, arctan(0.0));
   outstring(1, ",");
   outreal(1, ln(exp(1.0)));
   outstring(1, ",");
   outinteger(1, entier(3.7));
   outstring(1, ",");
   outinteger(1, entier(0.0 - 2.3))
end
