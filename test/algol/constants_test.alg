begin
    comment Test environmental constants;
    real r1, r2, r3;
    integer i1;

    r1 := maxreal;
    r2 := minreal;
    r3 := epsilon;
    i1 := maxint;

    outstring(1, "maxreal = ");
    outreal(1, r1);
    outterminator(1);

    outstring(1, "minreal = ");
    outreal(1, r2);
    outterminator(1);

    outstring(1, "epsilon = ");
    outreal(1, r3);
    outterminator(1);

    outstring(1, "maxint = ");
    outinteger(1, i1);
    outterminator(1)
end