begin comment Test void anonymous procedures passed to procedure parameters;
      procedure apply(f);
         procedure f;
      begin
         f
      end;
      apply(proc () void : outstring(1, "Hello"))
end;
