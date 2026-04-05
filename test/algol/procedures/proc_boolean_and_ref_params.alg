begin
    external java class java.lang.StringBuilder;

    ref(StringBuilder) procedure make;
    begin
        make := new StringBuilder("abc")
    end;

    boolean procedure ready;
    begin
        ready := true
    end;

    procedure use(maker, check);
        ref(StringBuilder) procedure maker;
        boolean procedure check;
    begin
        ref(StringBuilder) sb;
        sb := maker();
        outstring(1, sb.toString());
        if check() then outstring(1, "T") else outstring(1, "F")
    end;

    use(make, ready)
end
