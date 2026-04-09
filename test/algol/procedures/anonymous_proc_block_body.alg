begin comment Test block-bodied anonymous procedures with local declarations and a final result expression;
      integer procedure apply(f);
         integer procedure f;
      begin
         apply := f(5)
      end;

      outinteger(1,
         apply(
            proc (integer n) integer :
            begin
               integer result, i;
               result := 1;
               i := 1;
               while i <= n do
               begin
                  result := result * i;
                  i := i + 1
               end;
               result
            end))
end;
