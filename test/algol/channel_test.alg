begin
    comment Test channel-aware stream selection;
    comment channel 0 should go to stderr, channel 1 to stdout;
    
    outstring(0, "ERROR: This goes to stderr");
    outstring(1, "INFO: This goes to stdout");
    outinteger(0, 42);
    outinteger(1, 99);
    outreal(0, 3.14);
    outreal(1, 2.71);
    outchar(0, "ABC", 0);
    outchar(1, "XYZ", 1);
    outterminator(0);
    outterminator(1)
end
