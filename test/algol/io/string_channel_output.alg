comment Dynamic string-channel output sample for Milestone 29.;
begin
   string buf;

   buf := "";
   openstring(5, buf);
   outstring(5, "alpha");
   outterminator(5);
   outinteger(5, 42);
   closefile(5);

   outstring(1, "[");
   outstring(1, buf);
   outstring(1, "]")
end
