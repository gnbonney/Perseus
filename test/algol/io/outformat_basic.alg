comment Basic formatted stdout output sample for Milestone 30.;
begin
   integer i;
   real x;
   string s;

   i := 42;
   x := 3.14159;
   s := "Algol";

   outformat(1, "I5, F8.2, A10", i, x, s)
end
