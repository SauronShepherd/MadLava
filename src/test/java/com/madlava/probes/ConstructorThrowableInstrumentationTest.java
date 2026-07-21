package com.madlava.probes;

import com.madlava.instrumentation.CompositeTransformer;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.CheckClassAdapter;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

final class ConstructorThrowableInstrumentationTest {
    private Class<?> fixture;

    @BeforeEach void loadTransformedFixture() throws Exception {
        ProbeBridge.resetForTests();
        fixture = new TransformingLoader().loadClass("fixture.i03.ConstructorThrowableFixture");
    }

    @Test void countsOnlySuccessfulOutermostConstructorsAndHandlesSuperArgumentObjects() throws Exception {
        assertThrows(InvocationTargetException.class, () -> invoke("failedConstruction"));
        invoke("child");
        invoke("chained");
        invoke("overloaded");
        invoke("innerAndAnonymous");
        invoke("recursive");
        assertThrows(InvocationTargetException.class, () -> invoke("failedAfterInitialization"));

        ProbeBridge.Snapshot snapshot = ProbeBridge.snapshot();
        assertEquals(1L, snapshot.constructed().get("fixture.i03.ConstructorThrowableFixture$Child"));
        assertEquals(1L, snapshot.constructed().get("fixture.i03.ConstructorThrowableFixture$Helper"));
        assertEquals(1L, snapshot.constructed().get("fixture.i03.ConstructorThrowableFixture$Chained"));
        assertFalse(snapshot.constructed().containsKey("fixture.i03.ConstructorThrowableFixture$Parent"));
        assertFalse(snapshot.constructed().containsKey("fixture.i03.ConstructorThrowableFixture$FailedChild"));
        assertFalse(snapshot.constructed().containsKey("fixture.i03.ConstructorThrowableFixture$FailedParent"));
        assertEquals(3L, snapshot.constructed().get("fixture.i03.ConstructorThrowableFixture$Overloaded"));
        assertEquals(1L, snapshot.constructed().get("fixture.i03.ConstructorThrowableFixture$Outer"));
        assertEquals(1L, snapshot.constructed().get("fixture.i03.ConstructorThrowableFixture$Outer$Inner"));
        assertEquals(1L, snapshot.constructed().get("fixture.i03.ConstructorThrowableFixture$1"));
        assertEquals(4L, snapshot.constructed().get("fixture.i03.ConstructorThrowableFixture$Recursive"));
        assertFalse(snapshot.constructed().containsKey("fixture.i03.ConstructorThrowableFixture$FailedAfterInitialization"));
    }

    @Test void separatesCreationExplicitThrowAndPropagationWithoutLeakingMessages() throws Exception {
        String secret = "MADLAVA_SECRET_EXCEPTION_MESSAGE_84721";
        Throwable created = (Throwable) invoke("createdOnly", new Class<?>[]{String.class}, secret);
        assertSame(created, created);
        InvocationTargetException rethrow = assertThrows(InvocationTargetException.class,
                () -> invoke("rethrowSame", new Class<?>[]{Throwable.class}, created));
        assertSame(created, rethrow.getCause());
        assertThrows(InvocationTargetException.class,
                () -> invoke("wrap", new Class<?>[]{Throwable.class, String.class}, created, secret));

        ProbeBridge.Snapshot snapshot = ProbeBridge.snapshot();
        String type = "fixture.i03.ConstructorThrowableFixture$SecretException";
        assertEquals(2L, snapshot.throwableCreated().get(type));
        assertEquals(3L, snapshot.explicitThrows().get(type), "throw, same-object rethrow, and wrapper throw");
        assertTrue(snapshot.propagations().get(type) >= 2L);
        assertFalse(snapshot.jfrAvailable(), "JFR-unavailable state must be explicit");
        assertFalse(snapshot.toString().contains(secret));
        assertFalse(snapshot.constructed().toString().contains(secret));
        assertFalse(snapshot.throwableCreated().toString().contains(secret));
    }

    @Test void callbackFailuresDoNotChangeConstructionOrThrowableIdentity() throws Exception {
        ProbeBridge.injectFailureForTests(() -> { throw new AssertionError("agent-callback-failure"); });
        assertNotNull(invoke("child"));
        Throwable original=new IllegalStateException("application-secret");
        InvocationTargetException failure=assertThrows(InvocationTargetException.class,
                () -> invoke("rethrowSame",new Class<?>[]{Throwable.class},original));
        assertSame(original,failure.getCause());
        assertTrue(ProbeBridge.snapshot().constructed().isEmpty());
    }

    @Test void transformedLoaderIsNotRetainedByProbeState() throws Exception {
        WeakReference<ClassLoader> reference=createCollectableLoader();
        for(int i=0;i<80&&reference.get()!=null;i++){System.gc();Thread.sleep(10);}
        assertNull(reference.get(),"probe state must not strongly retain application class loaders");
    }

    private static WeakReference<ClassLoader> createCollectableLoader() throws Exception {
        TransformingLoader loader=new TransformingLoader();
        Class<?> type=loader.loadClass("fixture.i03.ConstructorThrowableFixture");
        type.getMethod("child").invoke(null);
        WeakReference<ClassLoader> result=new WeakReference<>(loader);
        type=null;loader=null;ProbeBridge.resetForTests();
        return result;
    }

    private Object invoke(String method) throws Exception { return invoke(method, new Class<?>[0]); }
    private Object invoke(String method, Class<?>[] types, Object... args) throws Exception {
        Method target = fixture.getMethod(method, types);
        return target.invoke(null, args);
    }

    private static final class TransformingLoader extends ClassLoader {
        private final CompositeTransformer transformer = new CompositeTransformer("fixture.i03");
        private TransformingLoader() { super(ConstructorThrowableInstrumentationTest.class.getClassLoader()); }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null && name.startsWith("fixture.i03.")) loaded = findClass(name);
                if (loaded == null) loaded = super.loadClass(name, false);
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        }
        @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
            String resource = name.replace('.', '/') + ".class";
            try (InputStream input = getParent().getResourceAsStream(resource)) {
                if (input == null) throw new ClassNotFoundException(name);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096]; int read;
                while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
                byte[] original = output.toByteArray();
                byte[] transformed = transformer.transform(null, this, name.replace('.','/'), null, null, original);
                if (transformed == null) throw new ClassNotFoundException("Transformation failed: " + name);
                StringWriter verification = new StringWriter();
                CheckClassAdapter.verify(new ClassReader(transformed), this, false, new PrintWriter(verification));
                if (verification.getBuffer().length() != 0) throw new ClassNotFoundException("ASM verification failed: " + verification);
                return defineClass(name, transformed, 0, transformed.length);
            } catch (ClassNotFoundException failure) { throw failure; }
            catch (Exception failure) { throw new ClassNotFoundException(name, failure); }
        }
    }
}
