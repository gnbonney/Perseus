begin
    comment
        Adapted from the Modified Report's definition of integer division.
        Intended to verify Perseus hardware representation for div;

    outinteger(1, 7 div 3);
    outstring(1, "\n");
    outinteger(1, -7 div 3);
    outstring(1, "\n");
    outinteger(1, 7 div -3);
    outstring(1, "\n");
    outinteger(1, -7 div -3);
    outstring(1, "\n")
end
