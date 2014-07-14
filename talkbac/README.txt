
Sample FSMAR Application Router Configuration:

T1: null, REGISTER, CONTINUE|ORIGINATING(From), talkbac
T2: talkbac, REGISTER, CONTINUE|ORIGINATING(From), proxy-registrar
T3: null, INVITE|NEW, ORIGINATING(From)|CONTINUE, proxy-registrar
T4: talkbac, INVITE|NEW, ORIGINATING(From)|CONTINUE, 3pcc
T5: 3pcc, INVITE|NEW, ORIGINATING(From)|CONTINUE, proxy-registrar
T6: null, MESSAGE|NEW, ORIGINATING(From)|CONTINUE, talkbac
T7: talkbac, MESSAGE|NEW, ORIGINATING(From)|CONTINUE, proxy-registrar



