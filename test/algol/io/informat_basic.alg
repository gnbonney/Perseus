comment Basic formatted input sample for Milestone 30.;
begin
   integer i;
   real x;
   string s;

   i := 0;
   x := 0.0;
   s := "";

   informat(1, "I3, F5.1, A5", i, x, s);

   outinteger(1, i);
   outterminator(1);
   outreal(1, x);
   outterminator(1);
   outstring(1, s)
end
