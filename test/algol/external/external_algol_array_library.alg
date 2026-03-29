begin
    real procedure sum3(a);
        real array a;
    begin
        integer i;
        real s;
        s := 0;
        for i := 1 step 1 until 3 do
            s := s + a[i];
        sum3 := s
    end
end
