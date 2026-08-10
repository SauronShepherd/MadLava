package com.madlava.diagnostics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdaptiveOverheadControllerTest {
    @Test void nonFiniteThresholdsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new AdaptiveOverheadController(Double.NaN,0.1,2,2));
        assertThrows(IllegalArgumentException.class,()->new AdaptiveOverheadController(0.2,Double.POSITIVE_INFINITY,2,2));
        assertThrows(IllegalArgumentException.class,()->new AdaptiveOverheadController(0.2,-0.1,2,2));
    }

    @Test void invalidMeasurementBreaksConsecutiveBreachSequence() {
        AdaptiveOverheadController controller=new AdaptiveOverheadController(0.2,0.1,2,2);
        assertEquals(AdaptiveOverheadController.State.NORMAL,controller.observe("x",0.3));
        assertEquals(AdaptiveOverheadController.State.NORMAL,controller.observe("x",Double.NaN));
        assertEquals(AdaptiveOverheadController.State.NORMAL,controller.observe("x",0.3));
        assertEquals(AdaptiveOverheadController.State.THROTTLED,controller.observe("x",0.3));
    }
}
