package com.madlava.methods;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MethodRuleListTest {
    @Test
    void missingHotReloadFilterSentinelProducesNoRules() {
        String missing = String.valueOf((Object) null);

        assertEquals(List.of(), MethodRuleList.split(missing));
        assertEquals(List.of(), MethodRuleList.split("  null  "));
    }

    @Test
    void validMethodRuleStillParsesNormally() {
        assertEquals(
                List.of("com.example.Service.call"),
                MethodRuleList.split("com.example.Service.call"));
    }
}
