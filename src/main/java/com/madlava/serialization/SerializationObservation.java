package com.madlava.serialization;

import com.madlava.io.RuntimeObservationBridge;
import java.util.Objects;
import java.util.function.LongSupplier;

public final class SerializationObservation {
    private SerializationObservation(){}
    public static <T> T observe(String implementation,String measurementMethod,String accuracy,LongSupplier position,CheckedSupplier<T> action)throws Exception{
        Objects.requireNonNull(position);Objects.requireNonNull(action);boolean root=RuntimeObservationBridge.serializationEnter();long before=root?position.getAsLong():0;
        try{T value=action.get();RuntimeObservationBridge.serializationExit(root,implementation,root?Math.max(0,position.getAsLong()-before):0,true,measurementMethod,accuracy);return value;}
        catch(Exception failure){RuntimeObservationBridge.serializationExit(root,implementation,0,false,measurementMethod,accuracy);throw failure;}
    }
    public static String kryoAvailability(ClassLoader loader){try{Class.forName("com.esotericsoftware.kryo.Kryo",false,loader);return "AVAILABLE_DYNAMIC";}catch(ClassNotFoundException absent){return "UNAVAILABLE";}catch(LinkageError incompatible){return "INCOMPATIBLE";}}
    @FunctionalInterface public interface CheckedSupplier<T>{T get()throws Exception;}
}
