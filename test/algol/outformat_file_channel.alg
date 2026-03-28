comment Formatted file-channel output sample for Milestone 30.;
begin
   string line;

   line := "";

   openfile(2, "milestone30_output.txt", "w");
   outformat(2, "I4, A6", 27, "Perseus");
   closefile(2);

   openfile(2, "milestone30_output.txt", "r");
   instring(2, line);
   closefile(2);

   outstring(1, line)
end
