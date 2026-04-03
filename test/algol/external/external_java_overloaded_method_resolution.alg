begin
    external java class java.util.ArrayList;
    external java class java.lang.Object;

    ref(ArrayList) items;
    ref(Object) removed;

    items := new ArrayList();
    items.add("abc");
    removed := items.remove(0);

    outstring(1, removed.toString())
end
