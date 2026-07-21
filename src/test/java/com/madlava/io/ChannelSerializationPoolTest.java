package com.madlava.io;

import com.madlava.pools.ObservedExecutorService;
import com.madlava.serialization.SerializationObservation;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.lang.ref.WeakReference;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ChannelSerializationPoolTest {
    @BeforeEach void reset(){RuntimeObservationBridge.resetForTests();}
    @Test void channelUsesPartialReturnedAmountsAndEof()throws Exception{
        PartialChannel delegate=new PartialChannel();ObservedByteChannel channel=new ObservedByteChannel(delegate,"socket-channel");
        assertEquals(2,channel.write(ByteBuffer.wrap(new byte[]{1,2,3,4})));assertEquals(2,channel.read(ByteBuffer.allocate(8)));assertEquals(-1,channel.read(ByteBuffer.allocate(8)));
        assertEquals(2,RuntimeObservationBridge.snapshot().io().get("channel-write|socket-channel").bytes);assertEquals(2,RuntimeObservationBridge.snapshot().io().get("channel-read|socket-channel").bytes);assertEquals(1,RuntimeObservationBridge.snapshot().io().get("channel-read|socket-channel").eof);
    }
    @Test void realJavaSerializationCountsOneRootWithExplicitAccuracy()throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();ObjectOutputStream output=new ObjectOutputStream(bytes);
        SerializationObservation.observe("java-object-stream","stream-position-delta","ESTIMATED",bytes::size,()->SerializationObservation.observe("java-nested","stream-position-delta","ESTIMATED",bytes::size,()->{output.writeObject(List.of("value"));output.flush();return null;}));
        assertEquals(1,RuntimeObservationBridge.snapshot().serialization().size());assertTrue(RuntimeObservationBridge.snapshot().serialization().keySet().iterator().next().contains("ESTIMATED"));assertEquals("AVAILABLE_DYNAMIC",SerializationObservation.kryoAvailability(getClass().getClassLoader()));assertEquals("UNAVAILABLE",SerializationObservation.kryoAvailability(new ClassLoader(null){}));
    }
    @Test void realKryoSerializationIsRootDeduplicatedAndTestOnly()throws Exception{
        Kryo kryo=new Kryo();kryo.setRegistrationRequired(false);ByteArrayOutputStream bytes=new ByteArrayOutputStream();Output output=new Output(bytes);
        SerializationObservation.observe("kryo","output-position-delta","ESTIMATED",output::position,()->SerializationObservation.observe("kryo-nested","output-position-delta","ESTIMATED",output::position,()->{kryo.writeClassAndObject(output,List.of("data"));output.flush();return null;}));
        assertEquals(1,RuntimeObservationBridge.snapshot().serialization().size());assertTrue(RuntimeObservationBridge.snapshot().serialization().keySet().iterator().next().startsWith("kryo|"));
    }
    @Test void loopbackNetworkCountsActualBytesAndAnonymizesEndpoint()throws Exception{
        try(ServerSocket server=new ServerSocket(0)){Thread receiver=new Thread(()->{try(Socket accepted=server.accept()){ObservedInputStream input=new ObservedInputStream(accepted.getInputStream(),"network|"+RuntimeObservationBridge.anonymizeEndpoint(String.valueOf(accepted.getRemoteSocketAddress())));byte[] data=new byte[8];assertEquals(3,input.read(data));}catch(Exception failure){throw new AssertionError(failure);}});receiver.start();try(Socket client=new Socket("127.0.0.1",server.getLocalPort())){String endpoint="127.0.0.1:"+server.getLocalPort();ObservedOutputStream output=new ObservedOutputStream(client.getOutputStream(),"network|"+RuntimeObservationBridge.anonymizeEndpoint(endpoint));output.write(new byte[]{1,2,3});output.flush();}receiver.join();String keys=RuntimeObservationBridge.snapshot().io().keySet().toString();assertFalse(keys.contains("127.0.0.1"));assertTrue(keys.contains("network|endpoint-"));}
    }
    @Test void executorPreservesValueFailureCancellationOrderingAndRejection()throws Exception{
        ObservedExecutorService executor=new ObservedExecutorService(Executors.newSingleThreadExecutor());
        assertEquals(7,executor.submit(()->7).get());Future<?> failure=executor.submit(()->{throw new IllegalStateException("application");});assertInstanceOf(IllegalStateException.class,assertThrows(ExecutionException.class,failure::get).getCause());
        Future<?> blocker=executor.submit(()->{try{Thread.sleep(500);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}});Future<?> cancelled=executor.submit(()->{});assertTrue(cancelled.cancel(false));assertThrows(CancellationException.class,cancelled::get);blocker.cancel(true);executor.shutdown();assertTrue(executor.awaitTermination(2,TimeUnit.SECONDS));assertThrows(RejectedExecutionException.class,()->executor.execute(()->{}));
        ObservedExecutorService.Snapshot snapshot=ObservedExecutorService.snapshot();assertTrue(snapshot.completed>=1);assertTrue(snapshot.failed>=1);assertTrue(snapshot.rejected>=1);
    }
    @Test void poolRegistryDoesNotRetainExecutor()throws Exception{WeakReference<Object> reference=createPool();for(int i=0;i<80&&reference.get()!=null;i++){System.gc();Thread.sleep(10);}assertNull(reference.get());ObservedExecutorService.snapshot();}
    private static WeakReference<Object> createPool(){java.util.concurrent.ExecutorService delegate=Executors.newSingleThreadExecutor();ObservedExecutorService observed=new ObservedExecutorService(delegate);delegate.shutdown();WeakReference<Object> reference=new WeakReference<>(delegate);observed=null;delegate=null;return reference;}
    private static final class PartialChannel implements ByteChannel{private boolean open=true,read;public int read(ByteBuffer target){if(read)return -1;read=true;target.put((byte)1).put((byte)2);return 2;}public int write(ByteBuffer source){source.get();source.get();return 2;}public boolean isOpen(){return open;}public void close(){open=false;}}
}
