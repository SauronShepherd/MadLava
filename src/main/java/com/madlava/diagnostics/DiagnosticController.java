package com.madlava.diagnostics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class DiagnosticController implements DiagnosticControllerMBean {
    private final Path outputRoot;private final int maxFiles,maxExecutions;private final long maxFileBytes,maxTotalBytes;private final Duration cooldown,maxAge;private final AtomicReference<String> effectiveConfiguration=new AtomicReference<>("{}");private final IncidentRecorder incidents=new IncidentRecorder(128);private volatile Instant lastExecution=Instant.EPOCH;private int executions;
    public DiagnosticController(Path outputRoot,int maxFiles,long maxFileBytes,long maxTotalBytes,int maxExecutions,Duration cooldown,Duration maxAge){this.outputRoot=outputRoot.toAbsolutePath().normalize();this.maxFiles=maxFiles;this.maxFileBytes=maxFileBytes;this.maxTotalBytes=maxTotalBytes;this.maxExecutions=maxExecutions;this.cooldown=cooldown;this.maxAge=maxAge;}
    @Override public String getState(){return "RUNNING";}
    @Override public String getEffectiveConfiguration(){return effectiveConfiguration.get();}
    @Override public String reloadConfiguration(String candidate){if(candidate==null||!candidate.trim().startsWith("{")||!candidate.trim().endsWith("}")){incidents.record("configuration","INVALID_CANDIDATE","RETAIN_PREVIOUS");return "REJECTED";}effectiveConfiguration.set(candidate);incidents.record("configuration","VALID_CANDIDATE","ATOMIC_REPLACE");return "APPLIED";}
    @Override public synchronized String triggerThreadDump(){try{if(!reserve())return "THROTTLED";Path file=safeFile("thread-dump-"+System.currentTimeMillis()+".txt");StringBuilder text=new StringBuilder();for(Map.Entry<Thread,StackTraceElement[]> entry:Thread.getAllStackTraces().entrySet()){text.append('"').append(entry.getKey().getName()).append("\"\n");for(StackTraceElement frame:entry.getValue()){if(text.length()>=maxFileBytes)break;text.append("  at ").append(frame).append('\n');}if(text.length()>=maxFileBytes)break;}Files.createDirectories(outputRoot);Files.writeString(file,text.substring(0,(int)Math.min(text.length(),maxFileBytes)),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);retain();incidents.record("threadDump","MANUAL_REQUEST","CREATED");return file.getFileName().toString();}catch(Exception failure){incidents.record("threadDump","WRITE_FAILURE","ISOLATED");return "FAILED";}}
    @Override public String triggerHeapDump(){incidents.record("heapDump","HPROF_MANUAL_ONLY","UNAVAILABLE_WITHOUT_EXPLICIT_PROVIDER");return "UNAVAILABLE";}
    public IncidentRecorder.Snapshot incidents(){return incidents.snapshot();}
    private boolean reserve(){Instant now=Instant.now();if(executions>=maxExecutions||Duration.between(lastExecution,now).compareTo(cooldown)<0)return false;executions++;lastExecution=now;return true;}
    private Path safeFile(String name){Path file=outputRoot.resolve(name).normalize();if(!file.startsWith(outputRoot))throw new IllegalArgumentException("Unsafe path");return file;}
    private void retain()throws IOException{if(!Files.isDirectory(outputRoot))return;List<Path> files=new ArrayList<>();try(java.util.stream.Stream<Path> stream=Files.list(outputRoot)){stream.filter(Files::isRegularFile).forEach(files::add);}Instant cutoff=Instant.now().minus(maxAge);for(Path file:files)if(Files.getLastModifiedTime(file).toInstant().isBefore(cutoff))Files.deleteIfExists(file);files.removeIf(path->!Files.exists(path));files.sort(Comparator.comparingLong(this::modified));long total=0;for(Path file:files)total+=size(file);while(files.size()>maxFiles||total>maxTotalBytes){Path oldest=files.remove(0);total-=size(oldest);Files.deleteIfExists(oldest);}}
    private long modified(Path path){try{return Files.getLastModifiedTime(path).toMillis();}catch(IOException ignored){return Long.MIN_VALUE;}}
    private long size(Path path){try{return Files.size(path);}catch(IOException ignored){return 0;}}
}
