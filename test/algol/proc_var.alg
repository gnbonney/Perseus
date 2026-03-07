begin comment Test procedure variables - Milestone 13A;
      procedure hello;
      begin
         outstring(1, "Hello from procedure")
      end;
      procedure goodbye;
      begin
         outstring(1, "Goodbye from procedure")
      end;
      procedure P;
      begin
         P := hello;
         P;
         P := goodbye;
         P
      end;
      P
end;