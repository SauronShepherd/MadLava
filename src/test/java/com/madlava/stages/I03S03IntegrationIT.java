package com.madlava.stages;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class I03S03IntegrationIT {
    @Test void packagedAgentIntegratesConstructorAndThrowableSources() throws Exception {
        String revision=System.getProperty("revision","0.1.0-alpha.1");
        Path output=Path.of("target","i03-stage").toAbsolutePath();Files.createDirectories(output);Files.deleteIfExists(output.resolve("madlava.jsonl"));
        String java=Path.of(System.getProperty("java.home"),"bin","java").toString();
        Path processLog=output.resolve("child-process.log");Files.deleteIfExists(processLog);
        Process process=new ProcessBuilder(List.of(java,"-javaagent:"+Path.of("target","madlava-agent-"+revision+".jar").toAbsolutePath()+"=output="+output+",instrumentationInclude=example.app,jfrThrowables=true","-jar",Path.of("target","madlava-agent-"+revision+"-example.jar").toAbsolutePath().toString())).redirectErrorStream(true).redirectOutput(processLog.toFile()).start();
        try {
            boolean exited=assertTimeoutPreemptively(Duration.ofSeconds(35),()->process.waitFor(30,TimeUnit.SECONDS));
            String childOutput=Files.exists(processLog)?Files.readString(processLog,StandardCharsets.UTF_8):"";
            assertTrue(exited,"Child JVM did not exit. Output: "+childOutput);
            assertEquals(0,process.exitValue(),childOutput);
        } finally {
            if(process.isAlive())process.destroyForcibly().waitFor(5,TimeUnit.SECONDS);
        }
        String report=Files.readString(output.resolve("madlava.jsonl"));
        assertTrue(report.contains("\"successfulOutermostConstructors\""));assertTrue(report.contains("\"explicitThrows\""));assertTrue(report.contains("\"jfrState\""));assertFalse(report.contains("MADLAVA_PACKAGED_SECRET_91827"));
    }
}
