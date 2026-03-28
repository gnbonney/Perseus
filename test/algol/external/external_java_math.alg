comment External java static interop sample for Milestone 26A;
begin
    external java static(java.lang.Integer)
        integer procedure parseInt(string s);
    external java static(java.lang.Math)
        real procedure cos(real x);

    outinteger(1, parseInt("42"));
    outterminator(1);
    outreal(1, cos(0))
end
