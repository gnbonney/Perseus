comment Use the compiled Perseus standard environment through builtin input names.;
begin
   integer x, pos;
   real y;
   x := 0;
   y := 0.0;
   pos := 0;

   ininteger(1, x);
   inreal(1, y);
   inchar(1, "Hello", pos);

   outinteger(1, x);
   outstring(1, ",");
   outreal(1, y);
   outstring(1, ",");
   outinteger(1, pos)
end
