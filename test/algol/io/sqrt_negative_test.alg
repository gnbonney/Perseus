begin
    comment Test sqrt of negative number returns NaN;
    real result;
    result := sqrt(-1.0);
    outstring(1, "sqrt(-1.0) = ");
    outreal(1, result);
    outterminator(1)
end