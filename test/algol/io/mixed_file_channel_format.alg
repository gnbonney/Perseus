comment Mixed formatted and unformatted output through a file channel for Milestone 37.;
begin
   integer ch;
   string line;

   ch := 6;
   line := "";

   openfile(ch, "milestone37_mixed_file_output.txt", "w");
   outstring(ch, "N=");
   outformat(ch, "I3, A4", 5, "xy");
   outstring(ch, "!");
   closefile(ch);

   openfile(ch, "milestone37_mixed_file_output.txt", "r");
   instring(ch, line);
   closefile(ch);

   outstring(1, line)
end
