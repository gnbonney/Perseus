begin
    procedure make(x);
        value x; integer x;
        procedure thunk;
        begin
            outinteger(1, x);
            outstring(1, "\n");
        end;
    begin
        make := thunk;
    end;

    procedure p1;
    procedure p2;

    begin
        p1 := make(1);
        p2 := make(2);
        p1;
        p2;
    end;
end
