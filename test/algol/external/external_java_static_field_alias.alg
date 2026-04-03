begin
    external java class java.io.PrintStream;

    external java static(java.lang.System)
        ref(PrintStream) out as stdout;

    stdout.println("Hello from static field")
end
