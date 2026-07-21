package com.madlava.pools;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public final class ObservedExecutorService extends AbstractExecutorService {
    private static final AtomicLong IDS=new AtomicLong();
    private static final ConcurrentHashMap<Long,WeakReference<ExecutorService>> POOLS=new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long,Counters> METRICS=new ConcurrentHashMap<>();
    private final ExecutorService delegate;private final long id;
    public ObservedExecutorService(ExecutorService delegate){this.delegate=Objects.requireNonNull(delegate);this.id=IDS.incrementAndGet();POOLS.put(id,new WeakReference<>(delegate));METRICS.put(id,new Counters());}
    public long observationId(){return id;}
    @Override public <T> Future<T> submit(Callable<T> task){Counters counters=METRICS.get(id);counters.submitted.increment();try{return delegate.submit(()->{counters.started.increment();try{T value=task.call();counters.completed.increment();return value;}catch(Exception failure){counters.failed.increment();throw failure;}catch(Error failure){counters.failed.increment();throw failure;}});}catch(RejectedExecutionException rejected){counters.rejected.increment();throw rejected;}}
    @Override public Future<?> submit(Runnable task){return submit(()->{task.run();return null;});}
    @Override public <T> Future<T> submit(Runnable task,T result){return submit(()->{task.run();return result;});}
    @Override public void execute(Runnable command){Counters counters=METRICS.get(id);counters.submitted.increment();try{delegate.execute(()->{counters.started.increment();try{command.run();counters.completed.increment();}catch(Throwable failure){counters.failed.increment();throw failure;}});}catch(RejectedExecutionException rejected){counters.rejected.increment();throw rejected;}}
    public static Snapshot snapshot(){long submitted=0,started=0,completed=0,failed=0,rejected=0;int live=0;for(Long id:POOLS.keySet()){WeakReference<ExecutorService> reference=POOLS.get(id);if(reference==null||reference.get()==null){POOLS.remove(id);METRICS.remove(id);continue;}live++;Counters c=METRICS.get(id);if(c!=null){submitted+=c.submitted.sum();started+=c.started.sum();completed+=c.completed.sum();failed+=c.failed.sum();rejected+=c.rejected.sum();}}return new Snapshot(live,submitted,started,completed,failed,rejected);}
    @Override public void shutdown(){delegate.shutdown();}
    @Override public List<Runnable> shutdownNow(){return delegate.shutdownNow();}
    @Override public boolean isShutdown(){return delegate.isShutdown();}
    @Override public boolean isTerminated(){return delegate.isTerminated();}
    @Override public boolean awaitTermination(long timeout,TimeUnit unit)throws InterruptedException{return delegate.awaitTermination(timeout,unit);}
    private static final class Counters{final LongAdder submitted=new LongAdder(),started=new LongAdder(),completed=new LongAdder(),failed=new LongAdder(),rejected=new LongAdder();}
    public static final class Snapshot{public final int livePools;public final long submitted,started,completed,failed,rejected;private Snapshot(int livePools,long submitted,long started,long completed,long failed,long rejected){this.livePools=livePools;this.submitted=submitted;this.started=started;this.completed=completed;this.failed=failed;this.rejected=rejected;}public java.util.Map<String,Object> report(){return java.util.Map.of("livePools",livePools,"submitted",submitted,"started",started,"completed",completed,"failed",failed,"rejected",rejected);}}
}
