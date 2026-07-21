package com.madlava.agent;

import com.madlava.core.FeatureRegistry;
import com.madlava.core.FeatureState;
import com.madlava.instrumentation.CompositeTransformer;
import com.madlava.reporting.BoundedSnapshotQueue;
import com.madlava.reporting.JsonlWriter;
import com.madlava.reporting.SnapshotScheduler;
import com.madlava.probes.ProbeBridge;
import com.madlava.diagnostics.DiagnosticsRuntime;
import com.madlava.runtime.RuntimeContext;
import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Clock;

public final class MadLavaAgent {
    private MadLavaAgent() {}
    public static void premain(String agentArgs, Instrumentation instrumentation) { start(agentArgs,instrumentation); }
    public static void agentmain(String agentArgs, Instrumentation instrumentation) { start(agentArgs,instrumentation); }
    private static void start(String args,Instrumentation instrumentation){try{String version=MadLavaAgent.class.getPackage().getImplementationVersion();if(version==null)version="0.1.0-rc.2";Path output=parseOutput(args);FeatureRegistry registry=new FeatureRegistry();for(String id:new String[]{"heapUsage","nonHeapUsage","bufferPools","garbageCollection","threadStatistics","threadCpu","processResources","classLoaderInsights","jvmExecutionEngine","selfObservability"})registry.register(id,FeatureState.RUNNING);String include=option(args,"instrumentationInclude");if(include!=null&&!include.isBlank()){instrumentation.addTransformer(new CompositeTransformer(include),false);ProbeBridge.configureJfr(Boolean.parseBoolean(option(args,"jfrThrowables")));registry.register("instanceCounting",FeatureState.RUNNING);registry.register("throwables",FeatureState.RUNNING);}if(Boolean.parseBoolean(option(args,"runtimeObservation")))for(String id:new String[]{"streamIo","networkIo","serialization","threadPools"})registry.register(id,FeatureState.RUNNING);if(Boolean.parseBoolean(option(args,"diagnostics"))){DiagnosticsRuntime.start(output);for(String id:new String[]{"executionSampling","allocationProfiling","contentionProfiling","threadDumps","heapDiagnostics","offHeapDiagnostics","incidentRecording","overheadControl"})registry.register(id,FeatureState.RUNNING);}if(Boolean.parseBoolean(option(args,"sparkObservation")))registry.register("spark",FeatureState.RUNNING);RuntimeContext context=new RuntimeContext(instrumentation,Clock.systemUTC(),output,registry);BoundedSnapshotQueue queue=new BoundedSnapshotQueue(64);JsonlWriter writer=new JsonlWriter(queue,output.resolve("madlava.jsonl"));writer.start();SnapshotScheduler scheduler=new SnapshotScheduler(context,queue,version,sha256(args==null?"":args));scheduler.start(1);Runtime.getRuntime().addShutdownHook(new Thread(()->{scheduler.close();ProbeBridge.shutdownJfr();writer.close();},"madlava-shutdown"));System.err.println("MadLava "+version+" ready; output="+output.toAbsolutePath().normalize());}catch(Throwable failure){System.err.println("MadLava bootstrap disabled: "+failure.getClass().getSimpleName());}}
    static Path parseOutput(String args){if(args!=null)for(String part:args.split(",")){String[] kv=part.split("=",2);if(kv.length==2&&kv[0].trim().equals("output")){Path p=Paths.get(kv[1].trim()).toAbsolutePath().normalize();return p;}}return Paths.get("madlava-output").toAbsolutePath().normalize();}
    static String option(String args,String key){if(args!=null)for(String part:args.split(",")){String[] kv=part.split("=",2);if(kv.length==2&&kv[0].trim().equals(key))return kv[1].trim();}return null;}
    static String sha256(String value)throws Exception{byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder(64);for(byte b:digest)out.append(String.format("%02x",b));return out.toString();}
}
