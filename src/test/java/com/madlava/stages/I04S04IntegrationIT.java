package com.madlava.stages;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class I04S04IntegrationIT {
    @Test void packagedAgentReportsIoSerializationNetworkAndPoolsWithoutSecrets()throws Exception{
        String revision=System.getProperty("revision","0.1.0-alpha.2");Path output=Path.of("target","i04-stage").toAbsolutePath();Files.createDirectories(output);Files.deleteIfExists(output.resolve("madlava.jsonl"));String java=Path.of(System.getProperty("java.home"),"bin","java").toString();
        Process process=new ProcessBuilder(List.of(java,"-javaagent:"+Path.of("target","madlava-agent-"+revision+".jar").toAbsolutePath()+"=output="+output+",instrumentationInclude=example.app,runtimeObservation=true","-jar",Path.of("target","madlava-agent-"+revision+"-example.jar").toAbsolutePath().toString())).redirectErrorStream(true).start();assertTimeoutPreemptively(Duration.ofSeconds(30),()->assertEquals(0,process.waitFor(),new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8)));
        String report=Files.readString(output.resolve("madlava.jsonl"));for(String field:new String[]{"\"streamIo\"","\"observedLayers\"","\"networkIo\"","\"serialization\"","\"threadPools\""})assertTrue(report.contains(field));assertFalse(report.contains("MADLAVA_PACKAGED_SECRET_91827"));
    }
}
