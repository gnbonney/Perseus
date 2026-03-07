begin comment Test procedure parameters - Milestone 13B;
      procedure hello;
         outstring(1, "Hello");
      procedure callIt;
         procedure P;
         begin
            P
         end;
      begin
         callIt(hello)
      end
end;