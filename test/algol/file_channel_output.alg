comment Dynamic file-channel write/read sample for Milestone 29.;
begin
   string line;

   line := "";

   openfile(2, "milestone29_output.txt", "w");
   outstring(2, "Hello, file channel!");
   closefile(2);

   openfile(2, "milestone29_output.txt", "r");
   instring(2, line);
   closefile(2);

   outstring(1, line)
end
