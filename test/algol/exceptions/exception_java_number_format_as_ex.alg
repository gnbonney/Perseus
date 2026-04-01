comment Bind a caught Java exception and use the bound value inside the handler.;
begin
   string msg;

   external java static(java.lang.Integer) integer procedure parseInt(string s);

   msg := "unset";

   begin
      parseInt("not-a-number")
   exception
      when java(java.lang.NumberFormatException) as ex do
         msg := ex.getMessage()
   end;

   outstring(1, msg)
end
