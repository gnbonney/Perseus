begin
    comment Test stop and fault procedures;
    outstring(1, "Testing stop and fault procedures");
    outterminator(1);

    comment Call stop - this exits with code 0;
    stop();

    comment This should never be reached;
    outstring(1, "This should not print");
    outterminator(1);

    comment Call fault - this prints error and exits with code 1;
    fault("Test error message", 0.0)
end