begin real x,y,u; integer k;
      x:=5/13;y:=12/13;
  AA: u:=0.6*x-0.8*y;
      y:=0.8*x+0.6*y;
      x:=u; 
      k:=k+1;
      if k<1000 then goto AA
end