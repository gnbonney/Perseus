begin
    external(gnb.perseus.programs.ExternalAlgolLibrary)
        integer procedure hypot2(real a, b);

    outinteger(1, hypot2(3, 4))
end
