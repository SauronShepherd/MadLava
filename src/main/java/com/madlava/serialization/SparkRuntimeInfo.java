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
        Map<String, Object> cached = CACHE.get();
        if (cached != null) {
            return cached;
        }
        Map<String, Object> detected = new LinkedHashMap<>();
        String sparkVersion = invokeScalaObjectString("org.apache.spark.package$", "SPARK_VERSION");
        String scalaVersion = invokeScalaObjectString("scala.util.Properties$", "versionNumberString");
        detected.put("sparkVersion", sparkVersion);
        detected.put("scalaVersion", scalaVersion);
        detected.put("javaVersion", System.getProperty("java.version", "unknown"));
        detected.put("supported", supports(sparkVersion, scalaVersion));
        Map<String, Object> immutable = Map.copyOf(detected);
        CACHE.compareAndSet(null, immutable);
        return CACHE.get();
    }

    private static boolean supports(String sparkVersion, String scalaVersion) {
        if (sparkVersion == null || scalaVersion == null) {
            return false;
        }
        String binary = scalaBinary(scalaVersion);
        return ("3.5.9".equals(sparkVersion) && ("2.12".equals(binary) || "2.13".equals(binary)))
                || (("4.0.4".equals(sparkVersion) || "4.1.3".equals(sparkVersion) || "4.2.0".equals(sparkVersion))
                && "2.13".equals(binary));
    }

    private static String scalaBinary(String version) {
        int first = version.indexOf('.');
        int second = first < 0 ? -1 : version.indexOf('.', first + 1);
        return second < 0 ? version : version.substring(0, second);
    }

    private static String invokeScalaObjectString(String className, String methodName) {
        try {
            ClassLoader context = Thread.currentThread().getContextClassLoader();
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
