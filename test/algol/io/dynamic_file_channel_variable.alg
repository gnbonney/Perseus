comment Dynamic file-channel sample using a variable channel number.;
begin
   integer ch;
   string line;

   ch := 6;
   line := "";

   openfile(ch, "milestone37_dynamic_output.txt", "w");
   outstring(ch, "dynamic file channel");
   closefile(ch);

   openfile(ch, "milestone37_dynamic_output.txt", "r");
   instring(ch, line);
   closefile(ch);

   outstring(1, line)
end
