begin
   integer ch, i;
   real x;
   string s;

   ch := 6;
   i := 0;
   x := 0.0;
   s := "";

   openfile(ch, "milestone37_informat_output.txt", "w");
   outstring(ch, " 42  3.1 Hello");
   closefile(ch);

   openfile(ch, "milestone37_informat_output.txt", "r");
   informat(ch, "I3, F5.1, A5", i, x, s);
   closefile(ch);

   outstring(1, s);
   outstring(1, ":");
   outinteger(1, i);
   outstring(1, ":");
   outreal(1, x)
end
