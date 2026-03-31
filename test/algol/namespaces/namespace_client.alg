comment Draft Milestone 33 sample: client program using a namespaced separately compiled unit;
begin
    external(mylib.numeric.NamespaceLibraryTest) real procedure hypot2(real a, b);

    outreal(1, hypot2(3.0, 4.0))
end
