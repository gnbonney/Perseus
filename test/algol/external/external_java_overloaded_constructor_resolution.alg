begin
    external java class java.lang.StringBuilder;

    ref(StringBuilder) sb;

    sb := new StringBuilder("abc");

    outstring(1, sb.toString())
end
