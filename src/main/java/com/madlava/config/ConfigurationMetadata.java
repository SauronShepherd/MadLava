package com.madlava.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ConfigurationMetadata {
    public enum Type { BOOLEAN, INTEGER, NUMBER, STRING }
    public static final class Entry {
        private final String path; private final Type type; private final Object defaultValue; private final Double minimum; private final Double maximum; private final boolean secret;
        public Entry(String path, Type type, Object defaultValue, Double minimum, Double maximum, boolean secret) { this.path=path;this.type=type;this.defaultValue=defaultValue;this.minimum=minimum;this.maximum=maximum;this.secret=secret; }
        public String path(){return path;} public Type type(){return type;} public Object defaultValue(){return defaultValue;} public Double minimum(){return minimum;} public Double maximum(){return maximum;} public boolean secret(){return secret;}
    }
    private final Map<String,Entry> entries;
    public ConfigurationMetadata(Map<String,Entry> entries){this.entries=Collections.unmodifiableMap(new LinkedHashMap<>(entries));}
    public Map<String,Entry> entries(){return entries;}
    public static ConfigurationMetadata baseline(){Map<String,Entry> e=new LinkedHashMap<>(); add(e,"enabled",Type.BOOLEAN,true,null,null);add(e,"reporting.output",Type.STRING,"madlava-output",null,null);add(e,"reporting.enabled",Type.BOOLEAN,true,null,null);add(e,"reporting.intervalMillis",Type.INTEGER,1000,1d,86400000d);add(e,"configuration.strict",Type.BOOLEAN,true,null,null);add(e,"configuration.reload.enabled",Type.BOOLEAN,false,null,null);add(e,"configuration.reload.intervalSeconds",Type.INTEGER,30,1d,86400d);add(e,"configuration.hotReload.enabled",Type.BOOLEAN,false,null,null);
        add(e,"output.directory",Type.STRING,"./madlava-output",null,null);
        add(e,"reporting.snapshotIntervalSeconds",Type.INTEGER,1,1d,86400d);
        add(e,"reporting.shutdownSnapshotOnly",Type.BOOLEAN,false,null,null);
        add(e,"reporting.human.enabled",Type.BOOLEAN,false,null,null);
        add(e,"reporting.human.maxRows",Type.INTEGER,50,0d,1000000d);
        add(e,"reporting.human.truncate",Type.INTEGER,100,0d,1000000d);
        for(String section:new String[]{"methodProfiling","argumentGroups","sparkSerialization","sparkSerializationDetail","diagnostics"})
            add(e,"reporting.human.sections."+section+".maxRows",Type.INTEGER,50,0d,1000000d);
        add(e,"features.methodProfiling.enabled",Type.BOOLEAN,false,null,null);
        add(e,"features.methodProfiling.maxEntries",Type.INTEGER,2048,1d,1000000d);
        add(e,"features.methodProfiling.argumentGrouping.maxGroupsPerMethod",Type.INTEGER,256,1d,100000d);
        add(e,"features.methodTracing.enabled",Type.BOOLEAN,false,null,null);
        add(e,"features.methodTracing.sampleRate",Type.NUMBER,1.0,0d,1d);
        add(e,"features.sparkSerialization.enabled",Type.BOOLEAN,false,null,null);
        add(e,"features.sparkSerialization.profile",Type.STRING,"ALL",null,null);
        add(e,"features.sparkSerialization.rootClasses",Type.BOOLEAN,true,null,null);
        add(e,"features.sparkSerialization.maxGroups",Type.INTEGER,2048,1d,1000000d);
        add(e,"filters.methods.includes",Type.STRING,"",null,null);add(e,"filters.methods.excludes",Type.STRING,"",null,null);add(e,"security.token",Type.STRING,"",null,null,true);add(e,"safety.maxFeatureErrors",Type.INTEGER,10,1d,100000d);add(e,"safety.featureSnapshotTimeoutMillis",Type.INTEGER,1000,1d,60000d);add(e,"safety.globalSnapshotTimeoutMillis",Type.INTEGER,5000,1d,300000d);for(String id:new String[]{"heapUsage","nonHeapUsage","bufferPools","garbageCollection","threadStatistics","threadCpu","processResources","classLoaderInsights","jvmExecutionEngine","selfObservability"})add(e,"features."+id+".enabled",Type.BOOLEAN,true,null,null);return new ConfigurationMetadata(e);}
    private static void add(Map<String,Entry> entries,String path,Type type,Object value,Double min,Double max){add(entries,path,type,value,min,max,false);}
    private static void add(Map<String,Entry> entries,String path,Type type,Object value,Double min,Double max,boolean secret){entries.put(path,new Entry(path,type,value,min,max,secret));}
}
