package com.madlava.probes;

import java.lang.reflect.Method;
import java.time.Duration;
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
            Object settings=streamType.getMethod("enable",String.class).invoke(stream,"jdk.JavaExceptionThrow");
            settings.getClass().getMethod("withThreshold",Duration.class).invoke(settings,Duration.ZERO);
            Consumer<Object> callback=event->{try{Object recordedClass=event.getClass().getMethod("getClass",String.class).invoke(event,"thrownClass");String name=(String)recordedClass.getClass().getMethod("getName").invoke(recordedClass);ProbeBridge.jfrThrow(name);}catch(Throwable ignored){}};
            streamType.getMethod("onEvent",String.class,Consumer.class).invoke(stream,"jdk.JavaExceptionThrow",callback);
            close=streamType.getMethod("close");streamType.getMethod("startAsync").invoke(stream);state=State.RUNNING;
        }catch(ClassNotFoundException unavailable){state=State.UNAVAILABLE;}
        catch(Throwable failure){state=State.FAILED;}
    }
    @Override public void close(){try{if(stream!=null&&close!=null)close.invoke(stream);}catch(Throwable ignored){}finally{if(state==State.RUNNING)state=State.STOPPED;}}
}
