package com.madlava.spark;

import com.madlava.serialization.SparkRuntimeInfo;

import java.util.LinkedHashMap;
import java.util.Map;

/** Passive Spark compatibility probe. It never obtains or constructs Spark runtime state. */
public final class SparkCompatibility {
    private SparkCompatibility() {
    }

    public static Map<String, Object> probe(ClassLoader loader) {
        ClassLoader effective = loader == null ? SparkCompatibility.class.getClassLoader() : loader;
        Map<String, Object> runtime = SparkRuntimeInfo.detect(effective);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("javaVersion", Runtime.version().feature());
        result.put("sparkVersion", runtime.get("sparkVersion"));
        result.put("scalaBinaryVersion", runtime.get("scalaBinaryVersion"));
        result.put("mode", classPresent(effective, "org.apache.spark.sql.connect.SparkSession")
                ? "CONNECT_AVAILABLE" : "CLASSIC");
        result.put("state", compatibilityState(runtime));
        result.put("contextState", "NOT_YET_OBSERVED");
        result.put("source", "RUNTIME_METADATA");
        return result;
    }

    private static String compatibilityState(Map<String, Object> runtime) {
        Object spark = runtime.get("sparkVersion");
        Object scala = runtime.get("scalaBinaryVersion");
        if ("unknown".equals(spark) || "unknown".equals(scala)) {
            return "UNAVAILABLE";
        }
        return Boolean.TRUE.equals(runtime.get("supported")) ? "SUPPORTED" : "UNSUPPORTED";
    }

    private static boolean classPresent(ClassLoader loader, String name) {
        try {
            Class.forName(name, false, loader);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
