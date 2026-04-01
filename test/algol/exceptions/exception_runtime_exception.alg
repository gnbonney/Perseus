comment Catch the runtime exception raised by fault.;
begin
   integer recovered;

   recovered := 0;

   begin
      fault("boom", 1)
   exception
      when RuntimeException do
         recovered := 1
   end;

   outinteger(1, recovered)
end
