comment Mixed formatted and unformatted output through a string channel for Milestone 37.;
begin
   string buf;

   buf := "";
   openstring(5, buf);
   outstring(5, "A=");
   outformat(5, "I3, F5.1", 7, 2.5);
   outstring(5, ";");
   outinteger(5, 9);
   closefile(5);

   outstring(1, buf)
end
