package com.madlava.agent;
import static org.junit.jupiter.api.Assertions.*; import java.nio.file.Path; import org.junit.jupiter.api.Test;
class MadLavaAgentTest { @Test void outputArgumentIsNormalized(){Path p=MadLavaAgent.parseOutput("output=target/agent-output");assertTrue(p.isAbsolute());assertTrue(p.endsWith(Path.of("target","agent-output")));} @Test void hashIsStable() throws Exception {assertEquals(MadLavaAgent.sha256("x"),MadLavaAgent.sha256("x"));assertEquals(64,MadLavaAgent.sha256("x").length());}}
