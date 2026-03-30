comment Draft Milestone 33 sample: Perseus class extending an abstract external Java class;
begin
    external java class java.io.OutputStream;

    OutputStream class CountingStream;
    begin
        integer count;

        procedure write(b);
            value b;
            integer b;
        begin
            count := count + 1
        end;

        integer procedure writes;
            writes := count
    end;

    ref(CountingStream) s;

    s := new CountingStream;
    s.write(65);
    s.write(66);
    outinteger(1, s.writes)
end
