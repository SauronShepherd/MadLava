package com.madlava.methods;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MethodObservationPlanTest {
    @Test void planKeepsTraceArgsDistinctFromCount(){
        MethodObservationPlan plan=MethodObservationPlan.compile(List.of("a.B.c","a.B.d(*)"));
        assertEquals(MethodObservationMode.COUNT,plan.find("a.B","c","()V").orElseThrow().mode());
        assertEquals(MethodObservationMode.COUNT_BY_ARGS,plan.find("a.B","d","()V").orElseThrow().mode());
    }
}
