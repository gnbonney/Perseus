comment Closed file-channel operations should fail in a defined way.;
begin
   integer ch;

   ch := 6;
   openfile(ch, "milestone37_closed_output.txt", "w");
   closefile(ch);

   outstring(ch, "x")
exception
   when IllegalStateException do
      outinteger(1, 1)
end
