package com.madlava.methods;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ClassLoaderScopeTest {
 @Test void identitiesAreStableDistinctAndBootstrapIsExplicit(){ClassLoaderScope.resetForTests();ClassLoader a=new ClassLoader(null){};ClassLoader b=new ClassLoader(null){};String one=ClassLoaderScope.scope(a);assertEquals(one,ClassLoaderScope.scope(a));assertFalse(one.equals(ClassLoaderScope.scope(b)));assertEquals("bootstrap",ClassLoaderScope.scope(null));}
 @Test void identityLookupNeverInvokesApplicationEqualsOrHashCode(){
   ClassLoaderScope.resetForTests();
   ClassLoader a=new HostileLoader(); ClassLoader b=new HostileLoader();
   String first=ClassLoaderScope.scope(a); String second=ClassLoaderScope.scope(b);
   assertEquals(first,ClassLoaderScope.scope(a)); assertNotEquals(first,second);
 }
 private static final class HostileLoader extends ClassLoader {
   private HostileLoader(){super(null);} @Override public int hashCode(){throw new AssertionError("hashCode called");}
   @Override public boolean equals(Object other){throw new AssertionError("equals called");}
 }
}
