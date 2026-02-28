.source                  Hello.java
.class                   public gnb/algol/programs/Hello
.super                   java/lang/Object


.method                  public <init>()V
   .limit stack          1
   .limit locals         1
   .var 0 is             this Lgnb/algol/programs/Hello; from LABEL0x0 to LABEL0x5
   .line                 17
LABEL0x0:
   aload_0               
   invokespecial         java/lang/Object/<init>()V
   return                
LABEL0x5:
.end method              

.method                  public static main([Ljava/lang/String;)V
   .limit stack          3
   .limit locals         1
   .var 0 is             args [Ljava/lang/String; from LABEL0x0 to LABEL0x13
   .line                 19
LABEL0x0:
   getstatic             java/lang/System/out Ljava/io/PrintStream;
   ldc                   "Hello world"
   invokevirtual         java/io/PrintStream/print(Ljava/lang/String;)V
   .line                 20
   getstatic             java/lang/System/out Ljava/io/PrintStream;
   dconst_0              
   invokestatic          java/lang/Math/cos(D)D
   invokevirtual         java/io/PrintStream/print(D)V
   .line                 21
   return                
LABEL0x13:
.end method              

