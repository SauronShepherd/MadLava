package com.madlava.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Bounded, strict UTF-8 reader for the agent's small configuration document. */
final class ConfigurationFileReader {
    static final int MAX_BYTES = 1024 * 1024;
    private ConfigurationFileReader() { }

    static String read(Path path) throws IOException {
        if (path == null) throw new IllegalArgumentException("Configuration path is null");
        byte[] bytes;
        try (InputStream input = Files.newInputStream(path)) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        }
        if (bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("MadLava configuration exceeds " + MAX_BYTES + " bytes");
        }
        try {
            CharBuffer decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return decoded.toString();
        } catch (java.nio.charset.CharacterCodingException invalidUtf8) {
            throw new IllegalArgumentException("MadLava configuration is not valid UTF-8", invalidUtf8);
        }
    }
}
