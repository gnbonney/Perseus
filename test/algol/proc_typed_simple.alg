begin comment Test typed procedure references - Milestone 13C;
      real procedure getPi;
         begin
            getPi := 3.14159
         end;
      real procedure getZero;
         begin
            getZero := 0.0
         end;
      real procedure P;
      begin
         P := getPi;
         outreal(1, P);
         P := getZero;
         outreal(1, P)
      end
end;