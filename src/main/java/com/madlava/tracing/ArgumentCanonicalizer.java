package com.madlava.tracing;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Identity-free, bounded grouping representation for COUNT_BY_ARGS.
 *
 * <p>Scalar literals are never retained. Equality is preserved within one
 * profiler instance by a per-instance salted SHA-256 fingerprint, while
 * arbitrary objects continue to group by runtime type. The salt is never
 * reported, so the canonical value is intentionally not stable across JVM
 * runs.</p>
 */
public final class ArgumentCanonicalizer {
    private static final int FINGERPRINT_BYTES = 12;
    private final int maxStringLength;
    private final byte[] salt = new byte[32];
    private final MessageDigest digest;

    public ArgumentCanonicalizer(){this(256);}
    public ArgumentCanonicalizer(int maxStringLength){
        this.maxStringLength=Math.max(1,maxStringLength);
        new SecureRandom().nextBytes(salt);
        try { this.digest=MessageDigest.getInstance("SHA-256"); }
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }

    public List<String> canonicalize(Object[] values){
        if(values==null)return List.of();
        List<String> result=new ArrayList<>(values.length);
        for(Object value:values)result.add(canonicalize(value));
        return Collections.unmodifiableList(result);
    }

    public String canonicalize(Object value){
        if(value==null)return "null";
        Class<?> type=value.getClass();
        if(isSafeScalar(type,value))return fingerprint(type.getName(),String.valueOf(value));
        if(value instanceof Enum<?>) {
            Enum<?> e=(Enum<?>)value;
            return fingerprint(e.getDeclaringClass().getName(),e.name());
        }
        if(value instanceof Class<?>)return limit(((Class<?>)value).getName());
        if(type.isArray())return limit(type.getComponentType().getName()+"["+Array.getLength(value)+"]");
        return limit(canonicalizeClassName(type.getName()));
    }

    public String canonicalizeClassName(String name){
        if(name==null||name.isBlank())return "<unavailable>";
        int hidden=name.indexOf("/0x");if(hidden>=0)name=name.substring(0,hidden);
        int identity=name.lastIndexOf('@');if(identity>0)name=name.substring(0,identity);
        return limit(name);
    }

    private static boolean isSafeScalar(Class<?> type,Object value){
        return type==String.class||type==Boolean.class||type==Byte.class||type==Short.class
                ||type==Integer.class||type==Long.class||type==Float.class||type==Double.class
                ||type==Character.class||type==java.math.BigInteger.class||type==java.math.BigDecimal.class;
    }

    private String fingerprint(String type,String literal){
        byte[] hash;
        synchronized(digest){
            digest.reset();
            digest.update(salt);
            digest.update(type.getBytes(StandardCharsets.UTF_8));
            digest.update((byte)0);
            digest.update(literal.getBytes(StandardCharsets.UTF_8));
            hash=digest.digest();
        }
        StringBuilder out=new StringBuilder(type.length()+2+FINGERPRINT_BYTES*2);
        out.append(type).append('#');
        for(int i=0;i<FINGERPRINT_BYTES;i++)out.append(String.format("%02x",hash[i]));
        return limit(out.toString());
    }

    private String limit(String value){
        if(value.length()<=maxStringLength)return value;
        final String suffix="...[truncated]";
        if(maxStringLength<=suffix.length())return value.substring(0,maxStringLength);
        return value.substring(0,maxStringLength-suffix.length())+suffix;
    }
}
