package com.madlava.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationFileReaderTest {
    @TempDir Path temporary;

    @Test void rejectsConfigurationLargerThanTheBound() throws Exception {
        Path file=temporary.resolve("large.json");
        byte[] data=new byte[ConfigurationFileReader.MAX_BYTES+1];
        java.util.Arrays.fill(data,(byte)' ');
        Files.write(file,data);
        IllegalArgumentException failure=assertThrows(IllegalArgumentException.class,()->ConfigurationFileReader.read(file));
        assertTrue(failure.getMessage().contains("exceeds"));
    }

    @Test void rejectsMalformedUtf8RatherThanReplacingBytes() throws Exception {
        Path file=temporary.resolve("invalid.json");
        Files.write(file,new byte[]{'{','}',(byte)0xC3,(byte)0x28});
        IllegalArgumentException failure=assertThrows(IllegalArgumentException.class,()->ConfigurationFileReader.read(file));
        assertTrue(failure.getMessage().contains("valid UTF-8"));
    }
}
