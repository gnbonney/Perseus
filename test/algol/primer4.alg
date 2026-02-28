begin real x,y,u; integer k;
      x:=5/13;y:=12/13;
      for k=1 step 1 until 1000 do
      begin u:=0.6*x-0.8*y;
            y:=0.8*x+0.6*y;
            x:=u; 
      end
end