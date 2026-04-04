begin
   integer i;
   real x;
   boolean b;
   string s;

   i := 7;
   x := 1234.0;
   b := true;
   s := "ok";

   outformat(1, "I5.3, 2X, E10.2, /, A, 2X, L5", i, x, s, b)
end
