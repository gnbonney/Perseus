begin comment Test procedure variables - Milestone 13A;
      procedure hello;
         outstring(1, "Hello from procedure");
      procedure goodbye;
         outstring(1, "Goodbye from procedure");
      procedure P;
      begin
         P := hello;
         P;
         P := goodbye;
         P
      end;
      P
end;