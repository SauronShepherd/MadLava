package com.madlava.probes;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Optional JFR Throwable source loaded reflectively to preserve the Java 11 baseline. */
final class JfrThrowableMonitor implements AutoCloseable {
    enum State { DISABLED, RUNNING, UNAVAILABLE, FAILED, STOPPED }
    private volatile State state=State.DISABLED; private Object stream; private Method close;
    State state(){return state;}
    void start(){
        if(state==State.RUNNING)return;
        try{
            Class<?> streamType=Class.forName("jdk.jfr.consumer.RecordingStream");
            stream=streamType.getConstructor().newInstance();
            // RecordingStream uses its own worker. It must never keep the observed JVM alive.
            try { streamType.getMethod("setDaemon", boolean.class).invoke(stream, true); }
            catch (NoSuchMethodException ignored) { /* Older supported JDK: close remains the shutdown path. */ }
            Object settings=streamType.getMethod("enable",String.class).invoke(stream,"jdk.JavaExceptionThrow");
            settings.getClass().getMethod("withThreshold",Duration.class).invoke(settings,Duration.ZERO);
            Consumer<Object> callback=event->{try{Object recordedClass=event.getClass().getMethod("getClass",String.class).invoke(event,"thrownClass");String name=(String)recordedClass.getClass().getMethod("getName").invoke(recordedClass);ProbeBridge.jfrThrow(name);}catch(Throwable ignored){}};
            streamType.getMethod("onEvent",String.class,Consumer.class).invoke(stream,"jdk.JavaExceptionThrow",callback);
            close=streamType.getMethod("close");
            CountDownLatch ready=new CountDownLatch(1);
            streamType.getMethod("onFlush",Runnable.class).invoke(stream,(Runnable)ready::countDown);
            Method start=streamType.getMethod("start");Object activeStream=stream;
            Thread worker=new Thread(()->{try{start.invoke(activeStream);}catch(Throwable ignored){}},"madlava-jfr-stream");
            worker.setDaemon(true);worker.start();
            if(ready.await(5,TimeUnit.SECONDS))state=State.RUNNING;
            else { state=State.FAILED; close(); state=State.FAILED; }
        }catch(ClassNotFoundException unavailable){state=State.UNAVAILABLE;}
        catch(Throwable failure){state=State.FAILED;try{close();}catch(Throwable ignored){}state=State.FAILED;}
    }
    @Override public void close(){
        Object activeStream=stream;Method closeMethod=close;
        stream=null;close=null;
        if(activeStream!=null&&closeMethod!=null){
            Thread closer=new Thread(()->{try{closeMethod.invoke(activeStream);}catch(Throwable ignored){}},"madlava-jfr-close");
            closer.setDaemon(true);closer.start();
            try{closer.join(1000);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
        }
        if(state==State.RUNNING)state=State.STOPPED;
    }
}
