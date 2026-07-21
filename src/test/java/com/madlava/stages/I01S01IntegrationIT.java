package com.madlava.stages;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
public class I01S01IntegrationIT { @Test void packagedFoundationIsSelfContained() throws Exception { String r=System.getProperty("revision","0.1.0-dev.1"); assertTrue(Files.size(Path.of("target","madlava-agent-"+r+".jar"))>0); assertTrue(Files.size(Path.of("target","madlava-"+r+"-complete-project.zip"))>0); } }
