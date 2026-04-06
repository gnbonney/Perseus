begin
   integer i;
   real x;
   string fmt;

   i := 42;
   x := 3.14159;
   fmt := "I5, F8.2";

   outformat(1, fmt, i, x)
end
