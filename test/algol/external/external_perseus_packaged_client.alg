comment Client program for packaged external Perseus linkage tests;
begin
    external(mylib.numeric.ExternalAlgolLibrary)
        real procedure hypot2(real a, b);

    outreal(1, hypot2(3, 4))
end
