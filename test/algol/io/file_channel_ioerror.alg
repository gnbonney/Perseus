comment File-channel error handling sample for Milestone 29.;
begin
   integer recovered;

   recovered := 0;

   begin
      openfile(7, "C:/definitely/not/a/real/path/perseus_m29.txt", "r")
   exception
      when IOError do
         recovered := 1
   end;

   outinteger(1, recovered)
end
