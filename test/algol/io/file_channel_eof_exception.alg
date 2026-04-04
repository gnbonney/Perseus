comment File-channel EOF handling sample for Milestone 37.;
begin
   integer ch, x, sawEof;

   ch := 8;
   x := 0;
   sawEof := 0;

   openfile(ch, "milestone37_eof_input.txt", "w");
   outstring(ch, "21");
   closefile(ch);

   openfile(ch, "milestone37_eof_input.txt", "r");
   ininteger(ch, x);

   begin
      ininteger(ch, x)
   exception
      when EOFException do
         sawEof := 1
   end;

   closefile(ch);
   outinteger(1, x);
   outstring(1, ",");
   outinteger(1, sawEof)
end
