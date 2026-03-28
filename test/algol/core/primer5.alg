begin
      comment Source: E.W. Dijkstra and J.A. Meijer, "A Primer of ALGOL 60
              Programming", Academic Press, 1962.
              https://archive.org/details/a_primer_of_algol_60_programming.;
      real eprox,term; integer n;
      eprox:=term:=1;
      for n:=1 step 1 until 20 do
      begin term:=term/n;
            eprox:=eprox+term;
      end;
      comment Output added for testing purposes to verify the approximation of e;
      outreal(1, eprox)
end