comment Catch a language-level FaultError raised by fault.;
begin
   integer recovered;

   recovered := 0;

   begin
      fault("boom", 1)
   exception
      when FaultError do
         recovered := 1
   end;

   outinteger(1, recovered)
end
