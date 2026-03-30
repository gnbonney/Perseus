comment Draft Milestone 33 sample: external Java class declaration and object interop;
begin
    external java class java.lang.StringBuilder;

    ref(StringBuilder) sb;

    sb := new StringBuilder;
    sb.append("Hello");
    sb.append(" ");
    sb.append("World");
    outstring(1, sb.toString)
end
