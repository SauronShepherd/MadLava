package com.madlava.tracing;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ArgumentCaptureTest {
    @Test void captureIsBoundedAndRedacted(){
        ArgumentCapture capture=new ArgumentCapture(new SafeArgumentRenderer(8,2),new ArgumentRedactor(List.of(1),List.of()),2);
        assertEquals(List.of("alpha","<redacted>"),capture.capture(new Object[]{"alpha","secret","ignored"}));
    }
}
