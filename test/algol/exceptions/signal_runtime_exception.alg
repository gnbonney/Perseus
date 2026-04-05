comment Explicit signal should raise and match the existing exception model.;
begin
   external java class java.lang.RuntimeException;
   integer ok;

   ok := 0;

   begin
      signal new RuntimeException("boom")
   exception
      when RuntimeException do
         ok := 1
   end;

   outinteger(1, ok)
end
