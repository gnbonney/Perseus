comment Use the compiled Perseus stdlib MathEnv unit through external linkage.;
begin
   external(perseus.lang.MathEnv) integer procedure sign(real E);
   external(perseus.lang.MathEnv) real procedure abs(real E);
   external(perseus.lang.MathEnv) real procedure sqrt(real E);
   external(perseus.lang.MathEnv) real procedure sin(real E);
   external(perseus.lang.MathEnv) real procedure cos(real E);
   external(perseus.lang.MathEnv) real procedure arctan(real E);
   external(perseus.lang.MathEnv) real procedure ln(real E);
   external(perseus.lang.MathEnv) real procedure exp(real E);

   outinteger(1, sign(-4.25));
   outstring(1, ",");
   outreal(1, abs(-2.5));
   outstring(1, ",");
   outreal(1, sqrt(9.0));
   outstring(1, ",");
   outreal(1, sin(0.0));
   outstring(1, ",");
   outreal(1, cos(0.0));
   outstring(1, ",");
   outreal(1, arctan(0.0));
   outstring(1, ",");
   outreal(1, ln(exp(1.0)))
end
