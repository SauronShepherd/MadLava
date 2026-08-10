package com.madlava.tracing;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ArgumentRenderingTest {
    @Test void safeRendererDoesNotCallApplicationToString(){
        Object value=new Object(){public String toString(){throw new AssertionError();}};
        assertTrue(new SafeArgumentRenderer().render(value).startsWith(value.getClass().getName()));
    }
    @Test void rendererBoundsStringsAndCollections(){
        String tiny=new SafeArgumentRenderer(4,2).render(List.of("abcdef","x","y"));
        assertTrue(tiny.length()<=4);
        assertTrue(tiny.contains("..."));

        String boundedCollection=new SafeArgumentRenderer(64,2).render(List.of("a","b","c"));
        assertTrue(boundedCollection.length()<=64);
        assertTrue(boundedCollection.contains("truncated"));
    }
    @Test void redactionIsByIndexAndPattern(){
        ArgumentRedactor redactor=new ArgumentRedactor(List.of(1),List.of(".*secret.*"));
        assertEquals("<redacted>",redactor.redact(1,"safe"));
        assertEquals("<redacted>",redactor.redact(0,"secret-value"));
    }
}
