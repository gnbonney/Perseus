begin comment Test typed procedure references - Milestone 13C;
      real procedure makeOne;
         begin
            makeOne := 1.0
         end;
      real procedure doubleIt;
         real x;
         begin
            doubleIt := x * 2.0
         end;
      real procedure P;
      begin
         P := makeOne;
         outreal(1, P);
         P := doubleIt;
         x := 3.5;
         outreal(1, P)
      end
end;