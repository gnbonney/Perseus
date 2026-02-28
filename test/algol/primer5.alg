begin real eprox,term; integer n;
      eprox:=term:=1;
      for n:=1 step 1 until 20 do
      begin term:=term/n;
            eprox:=eprox+term;
      end
end