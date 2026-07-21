package com.madlava.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class RuntimeObservationBridgeTest {
    @BeforeEach void reset(){RuntimeObservationBridge.resetForTests();}
    @Test void usesActualReadOutcomesAndRetainsEachObservedLayer()throws Exception{
        ObservedInputStream inner=new ObservedInputStream(new ByteArrayInputStream(new byte[]{1,2,3}),"physical-file");
        ObservedInputStream outer=new ObservedInputStream(inner,"buffer-wrapper");byte[] buffer=new byte[8];
        assertEquals(3,outer.read(buffer));assertEquals(-1,outer.read(buffer));
        RuntimeObservationBridge.Snapshot snapshot=RuntimeObservationBridge.snapshot();
        assertEquals(3,snapshot.io().get("read|physical-file").bytes);assertEquals(3,snapshot.io().get("read|buffer-wrapper").bytes);
        assertEquals(1,snapshot.io().get("read|physical-file").eof);assertEquals(1,snapshot.io().get("read|buffer-wrapper").eof);
    }
    @Test void failedOperationsReportErrorsNotSuccessfulBytes(){
        InputStream failing=new InputStream(){public int read()throws IOException{throw new IOException("payload-secret");}};
        assertThrows(IOException.class,()->new ObservedInputStream(failing,"failing-layer").read());
        RuntimeObservationBridge.Metric metric=RuntimeObservationBridge.snapshot().io().get("read|failing-layer");assertEquals(0,metric.bytes);assertEquals(1,metric.errors);
    }
    @Test void writerThreadAndAgentLayersAreExcluded()throws Exception{
        Thread thread=new Thread(()->RuntimeObservationBridge.io("write","application",12,true),"madlava-writer");thread.start();thread.join();
        RuntimeObservationBridge.io("write","com.madlava.reporting.JsonlWriter",12,true);assertTrue(RuntimeObservationBridge.snapshot().io().isEmpty());
    }
    @Test void serializationCountsOnlyRootAndLabelsMeasurementHonesty(){
        boolean root=RuntimeObservationBridge.serializationEnter();boolean nested=RuntimeObservationBridge.serializationEnter();
        RuntimeObservationBridge.serializationExit(nested,"java",7,true,"stream-position-delta","ESTIMATED");
        RuntimeObservationBridge.serializationExit(root,"java",7,true,"stream-position-delta","ESTIMATED");
        RuntimeObservationBridge.Snapshot snapshot=RuntimeObservationBridge.snapshot();assertEquals(1,snapshot.serialization().size());assertEquals(7,snapshot.serialization().values().iterator().next().bytes);
    }
    @Test void endpointAnonymizationIsStableAndDoesNotRetainAddress(){String endpoint="customer.internal.example:9443";String value=RuntimeObservationBridge.anonymizeEndpoint(endpoint);assertEquals(value,RuntimeObservationBridge.anonymizeEndpoint(endpoint));assertFalse(value.contains(endpoint));}
}
