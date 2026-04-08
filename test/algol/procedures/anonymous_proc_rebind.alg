begin comment Test rebinding a procedure name to an anonymous procedure;
      procedure p;
      begin
         p := proc () void : outstring(1, "Hello");
         p
      end;
      p
end;
