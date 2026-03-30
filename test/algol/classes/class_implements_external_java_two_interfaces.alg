comment Draft Milestone 33 sample: Perseus class implementing two external Java interfaces;
begin
    external java class java.lang.Runnable;
    external java class java.lang.AutoCloseable;
    external java class java.lang.Thread;

    class Greeter implements Runnable, AutoCloseable;
    begin
        procedure run;
        begin
            outstring(1, "hello")
        end;

        procedure close;
        begin
            outstring(1, " closed")
        end
    end;

    ref(Greeter) g;
    ref(Runnable) r;
    ref(AutoCloseable) c;
    ref(Thread) t;

    g := new Greeter;
    r := g;
    c := g;
    t := new Thread(r);
    t.start;
    t.join;
    c.close()
end
