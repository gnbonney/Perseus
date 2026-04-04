begin
    external java class gnb.perseus.compiler.testsupport.JavaOverloadAmbiguity;

    ref(JavaOverloadAmbiguity) helper;
    helper := new JavaOverloadAmbiguity();

    outstring(1, helper.pick("abc"))
end
