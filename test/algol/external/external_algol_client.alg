comment Client program for external Perseus linkage tests;
begin
    external(gnb.perseus.programs.ExternalAlgolLibrary)
        real procedure hypot2(real a, b);

    outreal(1, hypot2(3, 4))
end
