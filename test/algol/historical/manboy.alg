begin comment The "Man or Boy" test, devised by D.E. Knuth to test correct
              implementation of recursion and non-local references.
              ALGOL Bulletin 17.3 (1964).
              http://archive.computerhistory.org/resources/text/algol/algol_bulletin/A17/P24.HTM
              Correct answer: -67.;
      real procedure A(k, x1, x2, x3, x4, x5);
           value k; integer k;
           begin real procedure B;
                      begin k := k - 1;
                            B := A := A(k, B, x1, x2, x3, x4)
                      end;
                 if k <= 0 then A := x4 + x5 else B
           end;
      outreal(A(10, 1, -1, -1, 1, 0))
end;
