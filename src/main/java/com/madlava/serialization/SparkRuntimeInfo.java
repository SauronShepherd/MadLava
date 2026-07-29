package com.madlava.serialization;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Best-effort, dependency-free Spark/Scala runtime identification for the report. */
public final class SparkRuntimeInfo {
    private static final AtomicReference<Map<String, Object>> CACHE = new AtomicReference<>();

    private SparkRuntimeInfo() {
    }

    public static Map<String, Object> detect() {
        return detect(Thread.currentThread().getContextClassLoader());
    }

    public static Map<String, Object> detect(ClassLoader loader) {
        Map<String, Object> cached = CACHE.get();
        if (cached != null) {
            return cached;
        }
        Map<String, Object> detected = new LinkedHashMap<>();
        String sparkVersion = invokeScalaObjectString("org.apache.spark.package$", "SPARK_VERSION", loader);
        String scalaVersion = invokeScalaObjectString("scala.util.Properties$", "versionNumberString", loader);
        detected.put("sparkVersion", sparkVersion);
        detected.put("scalaVersion", scalaVersion);
        detected.put("scalaBinaryVersion", scalaBinary(scalaVersion));
        detected.put("javaVersion", System.getProperty("java.version", "unknown"));
        detected.put("supported", supports(sparkVersion, scalaVersion));
        Map<String, Object> immutable = Map.copyOf(detected);
        if (isResolved(immutable)) {
            CACHE.compareAndSet(null, immutable);
            return CACHE.get();
        }
        return immutable;
    }

    private static boolean isResolved(Map<String, Object> result) {
        return !"unknown".equals(result.get("sparkVersion"))
                && !"unknown".equals(result.get("scalaVersion"));
    }

    private static boolean supports(String sparkVersion, String scalaVersion) {
        if (sparkVersion == null || scalaVersion == null) {
            return false;
        }
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
            ClassLoader context = loader == null ? Thread.currentThread().getContextClassLoader() : loader;
            Class<?> type = Class.forName(className, false,
                    context == null ? SparkRuntimeInfo.class.getClassLoader() : context);
            Field moduleField = type.getField("MODULE$");
            Object module = moduleField.get(null);
            Method method = type.getMethod(methodName);
            Object value = method.invoke(module);
            return value == null ? "unknown" : String.valueOf(value);
        } catch (Throwable ignored) {
            return "unknown";
        }
    }
}
