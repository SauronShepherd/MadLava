package com.madlava.serialization;

import com.madlava.io.RuntimeObservationBridge;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Fail-open helpers around serialization operations. Profiling must never change application behaviour. */
public final class SerializationObservation {
    private SerializationObservation(){}

    public static <T> T observe(String implementation,String measurementMethod,String accuracy,LongSupplier position,CheckedSupplier<T> action)throws Exception{
        Objects.requireNonNull(position);
        Objects.requireNonNull(action);

        boolean entered=false;
        boolean root=false;
        try {
            root=RuntimeObservationBridge.serializationEnter();
            entered=true;
        } catch (Throwable ignored) {
            // Instrumentation is fail-open. If bookkeeping itself fails, still execute the application action.
        }

        long before=root?safePosition(position):-1L;
        try {
            T value=action.get();
            long bytes=-1L;
            if(root&&before>=0){
                long after=safePosition(position);
                if(after>=0)bytes=Math.max(0L,after-before);
            }
            if(entered)safeExit(root,implementation,bytes,true,measurementMethod,accuracy);
            return value;
        } catch (Exception failure) {
            if(entered)safeExit(root,implementation,-1L,false,measurementMethod,accuracy);
            throw failure;
        } catch (Error failure) {
            if(entered)safeExit(root,implementation,-1L,false,measurementMethod,accuracy);
            throw failure;
        }
    }

    private static long safePosition(LongSupplier position){
        try{return position.getAsLong();}catch(Throwable ignored){return -1L;}
    }

    private static void safeExit(boolean root,String implementation,long bytes,boolean success,String method,String accuracy){
        try{RuntimeObservationBridge.serializationExit(root,implementation,bytes,success,method,accuracy);}catch(Throwable ignored){/* fail-open */}
    }

    public static String kryoAvailability(ClassLoader loader){try{Class.forName("com.esotericsoftware.kryo.Kryo",false,loader);return "AVAILABLE_DYNAMIC";}catch(ClassNotFoundException absent){return "UNAVAILABLE";}catch(LinkageError incompatible){return "INCOMPATIBLE";}}
    @FunctionalInterface public interface CheckedSupplier<T>{T get()throws Exception;}
}
