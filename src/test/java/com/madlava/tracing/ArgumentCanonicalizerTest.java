package com.madlava.tracing;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ArgumentCanonicalizerTest {
    @Test void removesIdentityAndHiddenClassSuffix(){
        ArgumentCanonicalizer canonicalizer=new ArgumentCanonicalizer();
        assertEquals("Foo$$Lambda$4243",canonicalizer.canonicalizeClassName("Foo$$Lambda$4243/0xAAAA@111"));
        assertEquals("java.util.HashMap",canonicalizer.canonicalize(new java.util.HashMap<>()));
        assertEquals("java.lang.String[2]",canonicalizer.canonicalize((Object)new String[]{"A","B"}));
    }
    @Test void preservesLambdaOrdinalAndValueDifferences(){
        ArgumentCanonicalizer c=new ArgumentCanonicalizer();
        assertNotEquals(c.canonicalize("Foo$$Lambda$1/0xA@1"),c.canonicalize("Foo$$Lambda$2/0xB@2"));
        assertEquals(List.of("Foo$$Lambda$1/0xA@1","true"),c.canonicalize(new Object[]{"Foo$$Lambda$1/0xA@1",true}));
    }
}
