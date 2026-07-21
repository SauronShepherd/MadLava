package com.madlava.spark;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SparkCompatibility {
    private SparkCompatibility(){}
    public static Map<String,Object> probe(ClassLoader loader){Map<String,Object> result=new LinkedHashMap<>();result.put("javaVersion",Runtime.version().feature());try{Class<?> sparkContext=Class.forName("org.apache.spark.SparkContext",false,loader);Method version=sparkContext.getMethod("version");Object active=sparkContext.getMethod("getOrCreate").invoke(null);String spark=String.valueOf(version.invoke(active));result.put("sparkVersion",spark);result.put("scalaBinaryVersion",scala(loader));result.put("mode",classPresent(loader,"org.apache.spark.sql.connect.SparkSession")?"CONNECT_AVAILABLE":"CLASSIC");result.put("state",supported(spark,Runtime.version().feature()));result.put("source","RUNTIME_REFLECTION");}catch(ClassNotFoundException absent){result.put("state","UNAVAILABLE");result.put("reason","SPARK_CLASSES_ABSENT");}catch(Throwable incompatible){result.put("state","DEGRADED");result.put("reason","SIGNATURE_MISMATCH");}return result;}
    private static String supported(String spark,int java){if(spark.startsWith("3.5."))return java==11||java==17?"SUPPORTED":"UNSUPPORTED_COMBINATION";return "UNSUPPORTED_VERSION";}
    private static String scala(ClassLoader loader){try{Class<?> properties=Class.forName("scala.util.Properties",false,loader);String version=String.valueOf(properties.getMethod("versionNumberString").invoke(null));return version.startsWith("2.12.")?"2.12":"OTHER";}catch(Throwable ignored){return "UNAVAILABLE";}}
    private static boolean classPresent(ClassLoader loader,String name){try{Class.forName(name,false,loader);return true;}catch(Throwable ignored){return false;}}
}
