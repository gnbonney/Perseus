.source                  Example1.java
.class                   public gnb/algol/programs/Example1
.super                   java/lang/Object


.method                  public <init>()V
   .limit stack          1
   .limit locals         1
   .var 0 is             this Lgnb/algol/programs/Example1; from LABEL0x0 to LABEL0x5
   .line                 17
LABEL0x0:
   aload_0               
   invokespecial         java/lang/Object/<init>()V
   return                
LABEL0x5:
.end method              

.method                  public static main([Ljava/lang/String;)V
   .limit stack          6
   .limit locals         7
   .var 0 is             args [Ljava/lang/String; from LABEL0x0 to LABEL0x21
   .var 1 is             x D from LABEL0x2 to LABEL0x21
   .var 3 is             y D from LABEL0x4 to LABEL0x21
   .var 5 is             u D from LABEL0x11 to LABEL0x21
   .line                 20
LABEL0x0:
   dconst_0              
   dstore_1              
LABEL0x2:
   dconst_0              
   dstore_3              
   .line                 21
LABEL0x4:
   ldc2_w                0.6
   dload_1               
   dmul                  
   ldc2_w                0.8
   dload_3               
   dmul                  
   dsub                  
   dstore                5
   .line                 22
LABEL0x11:
   ldc2_w                0.8
   dload_1               
   dmul                  
   ldc2_w                0.6
   dload_3               
   dmul                  
   dadd                  
   dstore_3              
   .line                 23
   dload                 5
   dstore_1              
   .line                 24
   return                
LABEL0x21:
.end method              

