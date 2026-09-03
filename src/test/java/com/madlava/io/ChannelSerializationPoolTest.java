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
    @Test void executorPreservesImmediateNullTaskContract(){
        ObservedExecutorService executor=new ObservedExecutorService(Executors.newSingleThreadExecutor());
        try {
            assertThrows(NullPointerException.class,()->executor.submit((java.util.concurrent.Callable<Object>)null));
            assertThrows(NullPointerException.class,()->executor.submit((Runnable)null));
            assertThrows(NullPointerException.class,()->executor.submit((Runnable)null,"result"));
            assertThrows(NullPointerException.class,()->executor.execute(null));
        } finally { executor.shutdownNow(); }
    }
    @Test void poolRegistryDoesNotRetainExecutor()throws Exception{WeakReference<Object> reference=createPool();for(int i=0;i<80&&reference.get()!=null;i++){System.gc();Thread.sleep(10);}assertNull(reference.get());ObservedExecutorService.snapshot();}
    private static WeakReference<Object> createPool(){java.util.concurrent.ExecutorService delegate=Executors.newSingleThreadExecutor();ObservedExecutorService observed=new ObservedExecutorService(delegate);delegate.shutdown();WeakReference<Object> reference=new WeakReference<>(delegate);observed=null;delegate=null;return reference;}
    private static final class PartialChannel implements ByteChannel{private boolean open=true,read;public int read(ByteBuffer target){if(read)return -1;read=true;target.put((byte)1).put((byte)2);return 2;}public int write(ByteBuffer source){source.get();source.get();return 2;}public boolean isOpen(){return open;}public void close(){open=false;}}

    @Test void invokeAllCountsCallableFailureAtTheTaskBoundary() throws Exception {
        ObservedExecutorService.Snapshot before=ObservedExecutorService.snapshot();
        ObservedExecutorService executor=new ObservedExecutorService(Executors.newSingleThreadExecutor());
        try {
            java.util.List<Future<Integer>> futures=executor.invokeAll(java.util.List.of(
                    ()->1, ()->{throw new IllegalStateException("batch");}));
            assertEquals(1,futures.get(0).get());
            assertInstanceOf(IllegalStateException.class,assertThrows(ExecutionException.class,futures.get(1)::get).getCause());
            ObservedExecutorService.Snapshot after=ObservedExecutorService.snapshot();
            assertEquals(2L,after.submitted-before.submitted);
            assertEquals(2L,after.started-before.started);
            assertEquals(1L,after.completed-before.completed);
            assertEquals(1L,after.failed-before.failed);
        } finally { executor.shutdownNow(); }
    }
    @Test void poolRegistryTracksObservationWrapperRatherThanExternallyRetainedDelegate() throws Exception {
        ObservedExecutorService.Snapshot before=ObservedExecutorService.snapshot();
        java.util.concurrent.ExecutorService delegate=Executors.newSingleThreadExecutor();
        ObservedExecutorService observed=new ObservedExecutorService(delegate);
        assertEquals(before.livePools+1,ObservedExecutorService.snapshot().livePools);
        java.lang.ref.Reference.reachabilityFence(observed);
        WeakReference<Object> wrapper=new WeakReference<>(observed);
        observed=null;
        for(int i=0;i<80&&wrapper.get()!=null;i++){System.gc();Thread.sleep(10);}
        assertNull(wrapper.get());
        // The baseline may itself contain weakly reachable pools left by earlier tests.
        // Forcing GC above is allowed to collect those as well, so equality with the
        // initial live-pool count is not a stable invariant. The actual contract here
        // is that this wrapper is collectible even while its delegate remains strongly
        // reachable; after cleanup, the registry must therefore contain no more pools
        // than it did before this wrapper was created.
        assertTrue(ObservedExecutorService.snapshot().livePools <= before.livePools);
        delegate.shutdownNow();
    }

}
