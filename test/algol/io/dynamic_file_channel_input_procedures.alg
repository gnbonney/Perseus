comment Dynamic file-channel sample for ininteger, inreal, and inchar.;
begin
   integer ch, x, pos;
   real y;

   ch := 6;
   x := 0;
   y := 0.0;
   pos := 0;

   openfile(ch, "milestone37_dynamic_input.txt", "w");
   outstring(ch, "12 3.5 e");
   closefile(ch);

   openfile(ch, "milestone37_dynamic_input.txt", "r");
   ininteger(ch, x);
   inreal(ch, y);
   inchar(ch, "Hello", pos);
   closefile(ch);

   outinteger(1, x);
   outstring(1, ",");
   outreal(1, y);
   outstring(1, ",");
   outinteger(1, pos)
end
