package com.madlava.verification.spark;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/**
 * Non-interactive child process that invokes the real Spark serializer classes.
 * It intentionally uses reflection so the generic unit-test lane does not require Spark.
 */
public final class SparkSerializerProcessApp {
    private SparkSerializerProcessApp() {
    }

    public static void main(String[] arguments) throws Exception {
        String engine = arguments.length == 0 ? "java" : arguments[0];
        boolean kryo = engine.startsWith("kryo");
        boolean forceFailure = engine.endsWith("failure");
        String sparkVersion = sparkVersion();
        String scalaVersion = scalaVersion();
        requireExpectedVersion("madlava.spark.expected.version", sparkVersion);
        requireExpectedPrefix("madlava.spark.expected.scala", scalaVersion);

        Object sparkConf = newSparkConf();
        set(sparkConf, "spark.serializer", kryo
                ? "org.apache.spark.serializer.KryoSerializer"
                : "org.apache.spark.serializer.JavaSerializer");
        set(sparkConf, "spark.kryo.registrationRequired", Boolean.toString(forceFailure));
        set(sparkConf, "spark.kryo.referenceTracking", "true");
        if (forceFailure) {
            set(sparkConf, "spark.kryo.classesToRegister", Payload.class.getName());
        }

        Class<?> serializerType = Class.forName(kryo
                ? "org.apache.spark.serializer.KryoSerializer"
                : "org.apache.spark.serializer.JavaSerializer");
        Object serializer = newSerializer(serializerType, sparkConf);
        Object instance = serializer.getClass().getMethod("newInstance").invoke(serializer);

        Payload payload = new Payload("MADLAVA_PRIVATE_PAYLOAD_12", new int[]{1, 2, 3, 5, 8});
        Object classTag = classTag(Payload.class);
        ByteBuffer bytes = (ByteBuffer) invoke(instance, "serialize", payload, classTag);
        int exactBytes = bytes.remaining();
        Payload restored = (Payload) invoke(instance, "deserialize", bytes.duplicate(), classTag);
        if (!payload.equals(restored)) {
            throw new AssertionError("Spark serializer changed the payload");
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Object serializationStream = invoke(instance, "serializeStream", output);
        invoke(serializationStream, "writeObject", payload, classTag);
        invoke(serializationStream, "flush");
        invoke(serializationStream, "close");

        Object deserializationStream = invoke(
                instance,
                "deserializeStream",
                new ByteArrayInputStream(output.toByteArray()));
        Payload streamRestored = (Payload) invoke(deserializationStream, "readObject", classTag);
        invoke(deserializationStream, "close");
        if (!payload.equals(streamRestored)) {
            throw new AssertionError("Spark stream serializer changed the payload");
        }

        boolean failureObserved = false;
        if (forceFailure) {
            try {
                Object unregisteredTag = classTag(UnregisteredPayload.class);
                invoke(instance, "serialize", new UnregisteredPayload("MADLAVA_PRIVATE_FAILURE_PAYLOAD_12"), unregisteredTag);
            } catch (InvocationTargetException failure) {
                failureObserved = true;
            }
            if (!failureObserved) {
                throw new AssertionError("Expected real Spark serialization failure was not observed");
            }
        }

        System.out.println("MADLAVA_SPARK_SERIALIZER_OK=" + engine
                + ";sparkVersion=" + sparkVersion
                + ";scalaVersion=" + scalaVersion
                + ";bytes=" + exactBytes
                + ";streamBytes=" + output.size()
                + ";failureObserved=" + failureObserved);
    }


    private static Object newSerializer(Class<?> serializerType, Object sparkConf) throws Exception {
        Class<?> sparkConfType = Class.forName("org.apache.spark.SparkConf");
        try {
            return serializerType.getConstructor(sparkConfType).newInstance(sparkConf);
        } catch (NoSuchMethodException ignored) {
            return serializerType.getConstructor().newInstance();
        }
    }

    private static String sparkVersion() throws Exception {
        return scalaObjectString("org.apache.spark.package$", "SPARK_VERSION");
    }

    private static String scalaVersion() throws Exception {
        return scalaObjectString("scala.util.Properties$", "versionNumberString");
    }

    private static String scalaObjectString(String className, String methodName) throws Exception {
        Class<?> moduleClass = Class.forName(className);
        Object module = moduleClass.getField("MODULE$").get(null);
        return String.valueOf(moduleClass.getMethod(methodName).invoke(module));
    }

    private static void requireExpectedVersion(String property, String actual) {
        String expected = System.getProperty(property, "").trim();
        if (!expected.isEmpty() && !expected.equals(actual)) {
            throw new AssertionError("Expected " + property + '=' + expected + " but found " + actual);
        }
    }

    private static void requireExpectedPrefix(String property, String actual) {
        String expected = System.getProperty(property, "").trim();
        if (!expected.isEmpty() && !actual.startsWith(expected + '.')) {
            throw new AssertionError("Expected " + property + '=' + expected + ".x but found " + actual);
        }
    }

    private static Object newSparkConf() throws Exception {
        Class<?> type = Class.forName("org.apache.spark.SparkConf");
        return type.getConstructor(boolean.class).newInstance(false);
    }

    private static void set(Object sparkConf, String key, String value) throws Exception {
        sparkConf.getClass().getMethod("set", String.class, String.class).invoke(sparkConf, key, value);
    }

    private static Object classTag(Class<?> type) throws Exception {
        Class<?> moduleClass = Class.forName("scala.reflect.ClassTag$");
        Object module = moduleClass.getField("MODULE$").get(null);
        return moduleClass.getMethod("apply", Class.class).invoke(module, type);
    }

    private static Object invoke(Object target, String name, Object... arguments) throws Exception {
        Method selected = Arrays.stream(target.getClass().getMethods())
                .filter(method -> method.getName().equals(name))
                .filter(method -> method.getParameterCount() == arguments.length)
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodException(target.getClass().getName() + '.' + name));
        try {
            return selected.invoke(target, arguments);
        } catch (InvocationTargetException failure) {
            throw failure;
        }
    }

    public static final class Payload implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String label;
        private final int[] values;

        public Payload(String label, int[] values) {
            this.label = label;
            this.values = values.clone();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Payload)) {
                return false;
            }
            Payload that = (Payload) other;
            return Objects.equals(label, that.label) && Arrays.equals(values, that.values);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hashCode(label) + Arrays.hashCode(values);
        }
    }

    public static final class UnregisteredPayload {
        private final String value;

        public UnregisteredPayload(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
