package com.madlava.probes;

import com.madlava.instrumentation.CompositeTransformer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class RecordConstructorInstrumentationTest {
    @Test void recordCanonicalAndDelegatingConstructorsAreCountedOnceWhenRuntimeSupportsRecords() throws Exception {
        ProbeBridge.resetForTests();
        int feature=Runtime.version().feature();
        if(feature<16){assertEquals(11,feature,"Java 11 lane explicitly has no record class format");return;}
        Path root=Path.of("target","record-fixture");Path source=root.resolve("fixture/i03/ObservedRecord.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source,"package fixture.i03; public record ObservedRecord(int value){public ObservedRecord(){this(7);}}",StandardCharsets.UTF_8);
        String javac=Path.of(System.getProperty("java.home"),"bin","javac").toString();
        Process compiler=new ProcessBuilder(javac,"-d",root.toString(),source.toString()).inheritIO().start();
        assertEquals(0,compiler.waitFor());
        byte[] original=Files.readAllBytes(root.resolve("fixture/i03/ObservedRecord.class"));
        byte[] transformed=new CompositeTransformer("fixture.i03").transform(null,getClass().getClassLoader(),"fixture/i03/ObservedRecord",null,null,original);
        assertNotNull(transformed);
        Class<?> record=new ByteLoader().define("fixture.i03.ObservedRecord",transformed);
        Object value=record.getConstructor().newInstance();
        assertEquals(7,record.getMethod("value").invoke(value));
        assertEquals(1L,ProbeBridge.snapshot().constructed().get("fixture.i03.ObservedRecord"));
    }
    private static final class ByteLoader extends ClassLoader {
        private ByteLoader(){super(RecordConstructorInstrumentationTest.class.getClassLoader());}
        private Class<?> define(String name,byte[] bytes){return defineClass(name,bytes,0,bytes.length);}
    }
}
