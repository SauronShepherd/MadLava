package com.madlava.methods;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ArgumentKeyTest {
    @Test void equalRenderedTuplesShareValueEquality() {
        ArgumentKey first = new ArgumentKey(List.of("A", "true"));
        ArgumentKey second = new ArgumentKey(new ArrayList<>(List.of("A", "true")));
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        Map<ArgumentKey, Integer> groups = new HashMap<>();
        groups.merge(first, 1, Integer::sum); groups.merge(second, 1, Integer::sum);
        assertEquals(1, groups.size()); assertEquals(2, groups.get(first));
    }
}
