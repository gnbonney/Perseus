comment Client program for Milestone 26A external algol linkage tests;
begin
    external algol(gnb.perseus.programs.ExternalAlgolLibrary)
        real procedure hypot2(real a, b);

    outreal(1, hypot2(3, 4))
end
