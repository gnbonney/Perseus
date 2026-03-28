comment Formatted string-channel output sample for Milestone 30.;
begin
   string buf;

   buf := "";
   openstring(5, buf);
   outformat(5, "I4, F6.2, A6", 42, 3.14, "Hi");
   closefile(5);

   outstring(1, "[");
   outstring(1, buf);
   outstring(1, "]")
end
