package example.app;

import com.madlava.io.ObservedInputStream;
import com.madlava.io.ObservedOutputStream;
import com.madlava.pools.ObservedExecutorService;
import com.madlava.serialization.SerializationObservation;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class ExampleApplication {
    public static void main(String[] args) throws Exception { long total=0; for(int i=0;i<10000;i++) total+=i;new ExampleApplication();new ObservedException("MADLAVA_PACKAGED_SECRET_91827");try{throw new ObservedException("MADLAVA_PACKAGED_SECRET_91827");}catch(ObservedException expected){}ObservedInputStream input=new ObservedInputStream(new ByteArrayInputStream(new byte[]{1,2,3}),"example-input");while(input.read()!=-1){}ByteArrayOutputStream bytes=new ByteArrayOutputStream();ObservedOutputStream output=new ObservedOutputStream(bytes,"example-output");output.write(new byte[]{4,5});ObjectOutputStream objects=new ObjectOutputStream(bytes);SerializationObservation.observe("java","stream-position-delta","ESTIMATED",bytes::size,()->{objects.writeObject("safe-value");objects.flush();return null;});ObservedExecutorService executor=new ObservedExecutorService(Executors.newSingleThreadExecutor());executor.submit(()->7).get();executor.shutdown();executor.awaitTermination(2,TimeUnit.SECONDS);Thread.sleep(1200);System.out.println("MADLAVA_EXAMPLE_OK="+total); }
    static final class ObservedException extends RuntimeException { ObservedException(String message){super(message);} }
}
