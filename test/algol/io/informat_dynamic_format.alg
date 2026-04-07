comment Dynamic format-string sample for informat milestone 38 cleanup.;
begin
   integer i;
   real x;
   string s, fmt;

   i := 0;
   x := 0.0;
   s := "";
   fmt := "I3";
   fmt := fmt.concat(", F5.1, A5");

   informat(1, fmt, i, x, s);

   outinteger(1, i);
   outterminator(1);
   outreal(1, x);
   outterminator(1);
   outstring(1, s)
end
