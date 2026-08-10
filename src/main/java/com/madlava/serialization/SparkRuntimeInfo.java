package com.madlava.serialization;

import com.madlava.util.WeakIdentityMap;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/** Best-effort, dependency-free Spark/Scala runtime identification for the report. */
public final class SparkRuntimeInfo {
    /* Identity-weak keys avoid invoking application ClassLoader.equals/hashCode and never pin loaders. */
    private static final WeakIdentityMap<ClassLoader, Map<String, Object>> CACHE = new WeakIdentityMap<>();
    private static volatile Map<String, Object> bootstrapCache;

    private SparkRuntimeInfo() { }

    public static Map<String, Object> detect() {
        return detect(Thread.currentThread().getContextClassLoader());
    }

    public static Map<String, Object> detect(ClassLoader loader) {
        Map<String, Object> cached = loader == null ? bootstrapCache : CACHE.get(loader);
        if (cached != null) return cached;

        Map<String, Object> detected = new LinkedHashMap<>();
        String sparkVersion = invokeScalaObjectString("org.apache.spark.package$", "SPARK_VERSION", loader);
        String scalaVersion = invokeScalaObjectString("scala.util.Properties$", "versionNumberString", loader);
        detected.put("sparkVersion", sparkVersion);
        detected.put("scalaVersion", scalaVersion);
        detected.put("scalaBinaryVersion", scalaBinary(scalaVersion));
        detected.put("javaVersion", System.getProperty("java.version", "unknown"));
        detected.put("supported", supports(sparkVersion, scalaVersion));
        Map<String, Object> immutable = Map.copyOf(detected);

        // Do not cache unresolved results: Spark/Scala may become visible later during JVM bootstrap.
        if (!isResolved(immutable)) return immutable;
        if (loader == null) {
            synchronized (SparkRuntimeInfo.class) {
                if (bootstrapCache == null) bootstrapCache = immutable;
                return bootstrapCache;
            }
        }
        Map<String, Object> raced = CACHE.putIfAbsent(loader, immutable);
        return raced == null ? immutable : raced;
    }

    private static boolean isResolved(Map<String, Object> result) {
        return !"unknown".equals(result.get("sparkVersion"))
                && !"unknown".equals(result.get("scalaVersion"));
    }

    private static boolean supports(String sparkVersion, String scalaVersion) {
        if (sparkVersion == null || scalaVersion == null) return false;
        String binary = scalaBinary(scalaVersion);
        return (sparkVersion.startsWith("3.5.") && ("2.12".equals(binary) || "2.13".equals(binary)))
                || (sparkVersion.startsWith("4.") && "2.13".equals(binary));
    }

    private static String scalaBinary(String version) {
        int first = version.indexOf('.');
        int second = first < 0 ? -1 : version.indexOf('.', first + 1);
        return second < 0 ? version : version.substring(0, second);
    }

    private static String invokeScalaObjectString(String className, String methodName, ClassLoader loader) {
        try {
            ClassLoader context = loader == null ? SparkRuntimeInfo.class.getClassLoader() : loader;
            Class<?> type = Class.forName(className, false, context);
            Field moduleField = type.getField("MODULE$");
            Object module = moduleField.get(null);
            Method method = type.getMethod(methodName);
            Object value = method.invoke(module);
            // The real Spark/Scala APIs return String. Never execute an arbitrary toString() on
            // a spoofed or incompatible class supplied by an application ClassLoader.
            return value instanceof String ? (String) value : "unknown";
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    static void resetForTests() {
        CACHE.clear();
        bootstrapCache = null;
    }
}
