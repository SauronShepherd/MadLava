package com.madlava.probes;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class JfrThrowableMonitorTest {
    @Test void reportsUnavailableOrCapturesRealEventsWithoutMessages() throws Exception {
        ProbeBridge.resetForTests();
        try {
            ProbeBridge.configureJfr(true);
            if(Runtime.version().feature()<14){assertEquals("UNAVAILABLE",ProbeBridge.snapshot().jfrState());return;}
            assertEquals("RUNNING",ProbeBridge.snapshot().jfrState());
            for(int i=0;i<20;i++){try{throw new JfrSecretException("JFR_SECRET_66190");}catch(JfrSecretException ignored){}}
            long deadline=System.nanoTime()+3_000_000_000L;
            while(System.nanoTime()<deadline&&!ProbeBridge.snapshot().jfrThrows().containsKey(JfrSecretException.class.getName()))Thread.sleep(25);
            ProbeBridge.Snapshot snapshot=ProbeBridge.snapshot();
            assertTrue(snapshot.jfrThrows().getOrDefault(JfrSecretException.class.getName(),0L)>0L);
            assertFalse(snapshot.jfrThrows().toString().contains("JFR_SECRET_66190"));
        } finally { ProbeBridge.shutdownJfr(); }
    }
    static final class JfrSecretException extends RuntimeException{JfrSecretException(String message){super(message);}}
}
