comment Use the compiled Perseus standard environment fault helper through the builtin name.;
begin
   integer recovered;
   recovered := 0;

   begin
      fault("boom", 1.0)
   exception
      when RuntimeException do
         recovered := 1
   end;

   outinteger(1, recovered)
end
