package com.madlava.methods;

import com.madlava.util.WeakIdentityMap;
import java.util.concurrent.atomic.AtomicLong;

/** Collision-free, weakly-held class-loader identities for method metric keys. */
public final class ClassLoaderScope {
    private static final AtomicLong IDS = new AtomicLong();
    private static final WeakIdentityMap<ClassLoader, Long> IDS_BY_LOADER = new WeakIdentityMap<>();
    private ClassLoaderScope(){}

    public static String scope(ClassLoader loader){
        if(loader==null)return "bootstrap";
        long id = IDS_BY_LOADER.computeIfAbsent(loader, ignored -> IDS.incrementAndGet());
        return loader.getClass().getName()+"#"+Long.toUnsignedString(id);
    }

    static void resetForTests(){IDS_BY_LOADER.clear();IDS.set(0L);}
}
