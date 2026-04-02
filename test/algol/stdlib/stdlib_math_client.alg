comment Use the compiled Perseus stdlib MathEnv unit through external linkage.;
begin
   external(perseus.lang.MathEnv) integer procedure sign(real E);
   external(perseus.lang.MathEnv) real procedure abs(real E);

   outinteger(1, sign(-4.25));
   outstring(1, ",");
   outreal(1, abs(-2.5))
end
