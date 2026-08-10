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
    public DiagnosticController(Path outputRoot,int maxFiles,long maxFileBytes,long maxTotalBytes,int maxExecutions,Duration cooldown,Duration maxAge){
        if(outputRoot==null||maxFiles<1||maxFileBytes<1||maxTotalBytes<maxFileBytes||maxExecutions<1||cooldown==null||cooldown.isNegative()||maxAge==null||maxAge.isNegative())
            throw new IllegalArgumentException("Invalid diagnostic bounds");
        this.outputRoot=outputRoot.toAbsolutePath().normalize();this.maxFiles=maxFiles;this.maxFileBytes=maxFileBytes;this.maxTotalBytes=maxTotalBytes;this.maxExecutions=maxExecutions;this.cooldown=cooldown;this.maxAge=maxAge;
    }
    @Override public String getState(){return "RUNNING";}
    @Override public String getEffectiveConfiguration(){return effectiveConfiguration.get();}
    @Override public String reloadConfiguration(String candidate){
        if(candidate==null||!candidate.trim().startsWith("{")||!candidate.trim().endsWith("}")){
            incidents.record("configuration","INVALID_CANDIDATE","RETAIN_PREVIOUS");return "REJECTED";
        }
        // This controller is not wired to RuntimeConfigurationManager. Claiming APPLIED here used
        // to mutate only this MBean's local string while the agent kept running the old config.
        incidents.record("configuration","CONTROL_NOT_WIRED","RETAIN_PREVIOUS");
        return "UNAVAILABLE";
    }
    @Override public synchronized String triggerThreadDump(){
        try{
            if(!reserve())return "THROTTLED";
            Path file=safeFile("thread-dump-"+System.currentTimeMillis()+".txt");
            StringBuilder text=new StringBuilder();
            long charLimit=Math.min(maxFileBytes,(long)Integer.MAX_VALUE-8L);
            for(Map.Entry<Thread,StackTraceElement[]> entry:Thread.getAllStackTraces().entrySet()){
                appendBounded(text,"\"",charLimit);
                appendBounded(text,entry.getKey().getName(),charLimit);
                appendBounded(text,"\"\n",charLimit);
                for(StackTraceElement frame:entry.getValue()){
                    if(text.length()>=charLimit)break;
                    appendBounded(text,"  at ",charLimit);
                    appendBounded(text,String.valueOf(frame),charLimit);
                    appendBounded(text,"\n",charLimit);
                }
                if(text.length()>=charLimit)break;
            }
            Files.createDirectories(outputRoot);
            Files.write(file,truncateUtf8(text.toString(),maxFileBytes),StandardOpenOption.CREATE_NEW);
            retain(file);incidents.record("threadDump","MANUAL_REQUEST","CREATED");return file.getFileName().toString();
        }catch(Exception failure){incidents.record("threadDump","WRITE_FAILURE","ISOLATED");return "FAILED";}
    }
    private static void appendBounded(StringBuilder target,String value,long maximumChars){
        if(value==null||target.length()>=maximumChars)return;
        int remaining=(int)Math.min((long)Integer.MAX_VALUE-target.length(),maximumChars-target.length());
        if(remaining<=0)return;
        target.append(value,0,Math.min(value.length(),remaining));
    }
    static byte[] truncateUtf8(String value,long maximumBytes){
        byte[] encoded=value.getBytes(StandardCharsets.UTF_8);
        if(encoded.length<=maximumBytes)return encoded;
        long used=0L;
        int end=0;
        for(int index=0;index<value.length();){
            char first=value.charAt(index);
            int chars=1;
            int bytes;
            if(Character.isHighSurrogate(first)&&index+1<value.length()&&Character.isLowSurrogate(value.charAt(index+1))){
                chars=2;bytes=4;
            }else if(first<=0x7F)bytes=1;
            else if(first<=0x7FF)bytes=2;
            else bytes=3; // also conservative for malformed lone surrogates, which Java replaces.
            if(used+bytes>maximumBytes)break;
            used+=bytes;index+=chars;end=index;
        }
        return value.substring(0,end).getBytes(StandardCharsets.UTF_8);
    }
    @Override public String triggerHeapDump(){incidents.record("heapDump","HPROF_MANUAL_ONLY","UNAVAILABLE_WITHOUT_EXPLICIT_PROVIDER");return "UNAVAILABLE";}
    public IncidentRecorder.Snapshot incidents(){return incidents.snapshot();}
    private boolean reserve(){Instant now=Instant.now();if(executions>=maxExecutions||Duration.between(lastExecution,now).compareTo(cooldown)<0)return false;executions++;lastExecution=now;return true;}
    private Path safeFile(String name){Path file=outputRoot.resolve(name).normalize();if(!file.startsWith(outputRoot))throw new IllegalArgumentException("Unsafe path");return file;}
    private void retain(Path protectedFile)throws IOException{if(!Files.isDirectory(outputRoot))return;List<Path> files=new ArrayList<>();try(java.util.stream.Stream<Path> stream=Files.list(outputRoot)){stream.filter(Files::isRegularFile).forEach(files::add);}Instant cutoff=Instant.now().minus(maxAge);for(Path file:new ArrayList<>(files))if(!file.equals(protectedFile)&&Files.getLastModifiedTime(file).toInstant().isBefore(cutoff))Files.deleteIfExists(file);files.removeIf(path->!Files.exists(path));files.sort(Comparator.comparingLong(this::modified));long total=0;for(Path file:files)total+=size(file);while(files.size()>maxFiles||total>maxTotalBytes){int victim=-1;for(int i=0;i<files.size();i++)if(!files.get(i).equals(protectedFile)){victim=i;break;}if(victim<0)break;Path oldest=files.remove(victim);total-=size(oldest);Files.deleteIfExists(oldest);}}
    private long modified(Path path){try{return Files.getLastModifiedTime(path).toMillis();}catch(IOException ignored){return Long.MIN_VALUE;}}
    private long size(Path path){try{return Files.size(path);}catch(IOException ignored){return 0;}}
}
