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
    @Test void wildcardFiltersAreIgnoredByExactObservationPlan(){
        MethodObservationPlan plan=MethodObservationPlan.compile(List.of("com.example.*.*","a.B.c(*)"));
        assertEquals(1,plan.rules().size());
        assertEquals(MethodObservationMode.COUNT_BY_ARGS,plan.find("a.B","c","()V").orElseThrow().mode());
    }

    @Test void countByArgsSuffixBeforeDescriptorIsParsedAndDescriptorSpecificRuleWins(){
        MethodObservationPlan plan=MethodObservationPlan.compile(List.of("a.B.m(*)","a.B.m(*)#(I)V"));
        assertEquals("(I)V",plan.find("a.B","m","(I)V").orElseThrow().descriptor());
        assertNull(plan.find("a.B","m","(Ljava/lang/String;)V").orElseThrow().descriptor());
    }

    @Test void exactObjectDescriptorsSurviveCompactRuleSplitting(){
        MethodObservationPlan plan=MethodObservationPlan.compile(MethodRuleList.split(
                "a.B.m(*)#(Ljava/lang/String;)V;x.Y.n#()Ljava/lang/Object;"));
        assertEquals(MethodObservationMode.COUNT_BY_ARGS,plan.find("a.B","m","(Ljava/lang/String;)V").orElseThrow().mode());
        assertTrue(plan.find("x.Y","n","()Ljava/lang/Object;").isPresent());
    }

}
