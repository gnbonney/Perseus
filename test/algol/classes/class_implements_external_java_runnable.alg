comment Draft Milestone 33 sample: Perseus class implementing an external Java interface;
begin
    external java class java.lang.Runnable;
    external java class java.lang.Thread;

    class Greeter implements Runnable;
    begin
        procedure run;
        begin
            outstring(1, "hello from thread")
        end
    end;

    ref(Greeter) g;
    ref(Thread) t;

    g := new Greeter;
    t := new Thread(g);
    t.start;
    t.join
end
