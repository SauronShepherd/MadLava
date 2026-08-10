package com.madlava.tracing;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ArgumentCanonicalizerTest {
    @Test void preservesTypeShapeAndRemovesLambdaIdentity(){
        ArgumentCanonicalizer canonicalizer=new ArgumentCanonicalizer();
        assertEquals("Foo$$Lambda$4243",canonicalizer.canonicalizeClassName("Foo$$Lambda$4243/0xAAAA@111"));
        assertEquals("java.util.HashMap",canonicalizer.canonicalize(new java.util.HashMap<>()));
        assertEquals("java.lang.String[2]",canonicalizer.canonicalize((Object)new String[]{"A","B"}));
    }
    @Test void scalarLiteralsAreFingerprintGroupedButNotRetained(){
        ArgumentCanonicalizer c=new ArgumentCanonicalizer();
        String one=c.canonicalize("secret-value");
        String same=c.canonicalize("secret-value");
        String other=c.canonicalize("different-value");
        assertEquals(one,same);
        assertNotEquals(one,other);
        assertTrue(one.startsWith("java.lang.String#"));
        assertFalse(one.contains("secret-value"));
        List<String> tuple=c.canonicalize(new Object[]{"private",true,42});
        assertEquals(3,tuple.size());
        assertTrue(tuple.stream().noneMatch(v->v.contains("private")||v.equals("true")||v.equals("42")));
    }
    @Test void subclassedNumericTypesNeverExecuteApplicationToString(){
        class HostileBigInteger extends java.math.BigInteger {
            HostileBigInteger(){ super("1"); }
            @Override public String toString(){ throw new AssertionError("application toString must not run"); }
        }
        class HostileBigDecimal extends java.math.BigDecimal {
            HostileBigDecimal(){ super("1"); }
            @Override public String toString(){ throw new AssertionError("application toString must not run"); }
        }
        ArgumentCanonicalizer canonicalizer=new ArgumentCanonicalizer();
        assertTrue(canonicalizer.canonicalize(new HostileBigInteger()).contains("HostileBigInteger"));
        assertTrue(canonicalizer.canonicalize(new HostileBigDecimal()).contains("HostileBigDecimal"));
        SafeArgumentRenderer renderer=new SafeArgumentRenderer();
        assertTrue(renderer.render(new HostileBigInteger()).contains("HostileBigInteger@"));
        assertTrue(renderer.render(new HostileBigDecimal()).contains("HostileBigDecimal@"));
    }

    @Test void configuredLengthIsAHardOutputBound(){
        ArgumentCanonicalizer canonicalizer=new ArgumentCanonicalizer(8);
        assertTrue(canonicalizer.canonicalize("a very long secret").length()<=8);
        SafeArgumentRenderer safe=new SafeArgumentRenderer(12,16);
        assertTrue(safe.render(java.util.List.of("abcdefghijk","lmnopqrstuv")).length()<=12);
        ToStringArgumentRenderer optIn=new ToStringArgumentRenderer(10);
        assertTrue(optIn.render("abcdefghijklmnopqrstuvwxyz").length()<=10);
        assertTrue(new SafeArgumentRenderer(3,2).render(new Object()).length()<=3);
    }

    @Test void separateProfilerInstancesDoNotExposeStableScalarFingerprints(){
        assertNotEquals(new ArgumentCanonicalizer().canonicalize("same"),new ArgumentCanonicalizer().canonicalize("same"));
    }
}
