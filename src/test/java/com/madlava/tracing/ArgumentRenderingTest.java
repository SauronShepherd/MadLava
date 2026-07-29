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
        assertTrue(new SafeArgumentRenderer(4,2).render(List.of("abcdef","x","y")).contains("...") || new SafeArgumentRenderer(4,2).render(List.of("abcdef","x","y")).contains("truncated"));
    }
    @Test void redactionIsByIndexAndPattern(){
        ArgumentRedactor redactor=new ArgumentRedactor(List.of(1),List.of(".*secret.*"));
        assertEquals("<redacted>",redactor.redact(1,"safe"));
        assertEquals("<redacted>",redactor.redact(0,"secret-value"));
    }
}
