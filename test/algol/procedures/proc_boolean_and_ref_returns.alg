begin
    external java class java.lang.StringBuilder;

    boolean procedure positive(x);
        value x;
        integer x;
    begin
        positive := x > 0
    end;

    ref(StringBuilder) procedure builder(text);
        value text;
        string text;
    begin
        builder := new StringBuilder(text)
    end;

    ref(StringBuilder) sb;

    if positive(1) then outstring(1, "T") else outstring(1, "F");
    if positive(0) then outstring(1, "T") else outstring(1, "F");
    sb := builder("abc");
    outstring(1, sb.toString())
end
