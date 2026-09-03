package com.madlava.probes;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.lang.ref.WeakReference;

/** Fail-open callback surface used by transformed application classes. */
public final class ProbeBridge {
    private static final int MAX_GROUPS = 4096;
    private static final ConcurrentHashMap<String, LongAdder> CONSTRUCTED = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> THROWABLE_CREATED = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> EXPLICIT_THROWS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> PROPAGATIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> JFR_THROWS = new ConcurrentHashMap<>();
    private static final JfrThrowableMonitor JFR = new JfrThrowableMonitor();
    private static final ThreadLocal<ArrayDeque<Construction>> CONSTRUCTIONS =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Boolean> CALLBACK = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static volatile Runnable failureInjection;

    private ProbeBridge() {}

    public static void constructorInitialized(Object instance, String type) {
        guarded(() -> {
            ArrayDeque<Construction> stack = CONSTRUCTIONS.get();
            LastCompletion previous = LAST_COMPLETION.get();
            if (previous != null && previous.instance.get() == instance) {
                decrement(CONSTRUCTED, previous.constructedBucket);
                if (previous.throwableBucket != null) decrement(THROWABLE_CREATED, previous.throwableBucket);
            }
            LAST_COMPLETION.remove();
            stack.push(new Construction(type));
        });
    }

    public static void constructorComplete(Object instance) {
        guarded(() -> {
            Construction frame = pop();
            if (frame != null) {
                String type = instance == null ? frame.type : instance.getClass().getName();
                String constructedBucket = increment(CONSTRUCTED, type);
                String throwableBucket = instance instanceof Throwable ? increment(THROWABLE_CREATED, type) : null;
                LAST_COMPLETION.set(new LastCompletion(instance, constructedBucket, throwableBucket));
            }
        });
    }

    public static void constructorFailed() { guarded(ProbeBridge::pop); }

    public static void explicitThrow(Throwable value) {
        guarded(() -> increment(EXPLICIT_THROWS, value == null ? "unknown" : value.getClass().getName()));
    }

    public static void propagated(Throwable value) {
        guarded(() -> increment(PROPAGATIONS, value == null ? "unknown" : value.getClass().getName()));
    }
    static void jfrThrow(String type){increment(JFR_THROWS,type);}
    public static void configureJfr(boolean enabled){if(enabled)JFR.start();else JFR.close();}
    public static void shutdownJfr(){JFR.close();}

    public static Snapshot snapshot() {
        return new Snapshot(copy(CONSTRUCTED), copy(THROWABLE_CREATED), copy(EXPLICIT_THROWS), copy(PROPAGATIONS),copy(JFR_THROWS),JFR.state().name());
    }

    static void resetForTests() {
        CONSTRUCTED.clear(); THROWABLE_CREATED.clear(); EXPLICIT_THROWS.clear(); PROPAGATIONS.clear();JFR_THROWS.clear();
        CONSTRUCTIONS.remove(); CALLBACK.remove(); LAST_COMPLETION.remove();
        failureInjection=null;
    }
    static void injectFailureForTests(Runnable failure){failureInjection=failure;}

    private static Construction pop() {
        ArrayDeque<Construction> stack = CONSTRUCTIONS.get();
        Construction result = stack.isEmpty() ? null : stack.pop();
        if (stack.isEmpty()) CONSTRUCTIONS.remove();
        return result;
    }

    private static String increment(ConcurrentHashMap<String, LongAdder> target, String key) {
        LongAdder existing = target.get(key);
        if (existing != null) { existing.increment(); return key; }
        synchronized (target) {
            existing = target.get(key);
            if (existing == null) {
                if (target.size() >= MAX_GROUPS - 1) key = "other";
                existing = target.computeIfAbsent(key, ignored -> new LongAdder());
            }
        }
        existing.increment();
        return key;
    }
    private static void decrement(ConcurrentHashMap<String, LongAdder> target, String key) {
        LongAdder value=target.get(key); if(value!=null)value.decrement();
    }

    private static Map<String, Long> copy(ConcurrentHashMap<String, LongAdder> source) {
        Map<String, Long> result = new LinkedHashMap<>();
        source.forEach((key, value) -> { long count=value.sum(); if(count!=0L)result.put(key,count); });
        return Collections.unmodifiableMap(result);
    }

    private static void guarded(Runnable callback) {
        if (CALLBACK.get()) return;
        CALLBACK.set(Boolean.TRUE);
        try { Runnable injected=failureInjection;if(injected!=null)injected.run();callback.run(); } catch (Throwable ignored) { /* application behavior wins */ }
        finally { CALLBACK.remove(); }
    }

    private static final ThreadLocal<LastCompletion> LAST_COMPLETION = new ThreadLocal<>();
    private static final class Construction { private final String type; private Construction(String type){this.type=type;} }
    private static final class LastCompletion {
        private final WeakReference<Object> instance;
        private final String constructedBucket;
        private final String throwableBucket;
        private LastCompletion(Object value,String constructedBucket,String throwableBucket){
            this.instance=new WeakReference<>(value);this.constructedBucket=constructedBucket;this.throwableBucket=throwableBucket;
        }
    }

    public static final class Snapshot {
        private final Map<String,Long> constructed, throwableCreated, explicitThrows, propagations,jfrThrows;
        private final String jfrState;
        private Snapshot(Map<String,Long> constructed, Map<String,Long> throwableCreated,
                         Map<String,Long> explicitThrows, Map<String,Long> propagations,Map<String,Long> jfrThrows,String jfrState) {
            this.constructed=constructed; this.throwableCreated=throwableCreated;
            this.explicitThrows=explicitThrows; this.propagations=propagations;this.jfrThrows=jfrThrows;this.jfrState=jfrState;
        }
        public Map<String,Long> constructed(){return constructed;}
        public Map<String,Long> throwableCreated(){return throwableCreated;}
        public Map<String,Long> explicitThrows(){return explicitThrows;}
        public Map<String,Long> propagations(){return propagations;}
        public Map<String,Long> jfrThrows(){return jfrThrows;}
        public String jfrState(){return jfrState;}
        public boolean jfrAvailable(){return "RUNNING".equals(jfrState);}
    }
}
