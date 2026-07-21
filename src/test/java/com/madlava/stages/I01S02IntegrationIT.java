package com.madlava.stages;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
public class I01S02IntegrationIT { @Test void packagedVerticalSliceProducesFinalReport() throws Exception { String r=System.getProperty("revision","0.1.0-dev.1"); Path out=Path.of("target","i01-s02-stage").toAbsolutePath(); Files.createDirectories(out); Files.deleteIfExists(out.resolve("madlava.jsonl")); String java=Path.of(System.getProperty("java.home"),"bin","java").toString(); Process p=new ProcessBuilder(List.of(java,"-javaagent:"+Path.of("target","madlava-agent-"+r+".jar").toAbsolutePath()+"=output="+out,"-jar",Path.of("target","madlava-agent-"+r+"-example.jar").toAbsolutePath().toString())).redirectErrorStream(true).start(); assertTimeoutPreemptively(Duration.ofSeconds(20),()->assertEquals(0,p.waitFor(),new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8))); String report=Files.readString(out.resolve("madlava.jsonl")); assertTrue(report.contains("\"schemaVersion\":3")); assertTrue(report.contains("\"final\":true")); } }
