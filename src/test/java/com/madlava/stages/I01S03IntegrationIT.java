package com.madlava.stages;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
public class I01S03IntegrationIT { @Test void certificationInputsMatchPackagedArtifacts() throws Exception { String r=System.getProperty("revision","0.1.0-dev.1"); try(JarFile jar=new JarFile(Path.of("target","madlava-agent-"+r+".jar").toFile())){ assertEquals("com.madlava.agent.MadLavaAgent",jar.getManifest().getMainAttributes().getValue("Premain-Class")); assertEquals("false",jar.getManifest().getMainAttributes().getValue("Can-Retransform-Classes")); } assertTrue(Files.exists(Path.of("target","madlava-report-viewer-"+r+".zip"))); assertTrue(Files.exists(Path.of("scripts","certify-i01.ps1"))); } }
