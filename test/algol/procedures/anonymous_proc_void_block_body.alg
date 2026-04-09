begin comment Test void block-bodied anonymous procedures with multiple statements;
      procedure apply(f);
         procedure f;
      begin
         f
      end;

      apply(
         proc () void :
         begin
            outstring(1, "Hello");
            outstring(1, " ");
            outstring(1, "there")
         end)
end;
