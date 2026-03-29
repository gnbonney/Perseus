begin
    external(gnb.perseus.programs.ExternalAlgolArrayLibrary) real procedure sum3(real array a);

    real array q[1:3];

    q[1] := 1.0;
    q[2] := 2.0;
    q[3] := 3.0;

    outreal(1, sum3(q))
end
