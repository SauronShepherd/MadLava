package com.madlava.tracing;

import java.util.*;

/** Bounded renderer that never calls application toString() for arbitrary objects. */
public final class SafeArgumentRenderer implements ArgumentRenderer {
    private static final String TRUNCATED = "...[truncated]";
    private final int maxLength, maxElements;

    public SafeArgumentRenderer(){this(512,16);}

    public SafeArgumentRenderer(int maxLength,int maxElements){
        this.maxLength=Math.max(1,maxLength);
        this.maxElements=Math.max(1,maxElements);
    }

    public String render(Object value){return limit(render(value,0));}

    private String render(Object value,int depth){
        if(value==null)return "null";
        Class<?> type=value.getClass();
        if(type==String.class||type==Boolean.class||type==Byte.class||type==Short.class||type==Integer.class
                ||type==Long.class||type==Float.class||type==Double.class||type==Character.class
                ||type==java.math.BigInteger.class||type==java.math.BigDecimal.class||type==UUID.class)
            return limit(String.valueOf(value));
        if(value instanceof Enum<?>)
            return limit(((Enum<?>)value).getDeclaringClass().getName()+"."+((Enum<?>)value).name());
        if(value instanceof Class<?>)return limit(((Class<?>)value).getName());
        if(depth>1)return limit("<"+type.getName()+">");
        if(type.isArray()){
            StringBuilder b=new StringBuilder("[");
            int length=java.lang.reflect.Array.getLength(value);
            int n=Math.min(length,maxElements);
            for(int i=0;i<n;i++){
                if(i>0)b.append(',');
                b.append(render(java.lang.reflect.Array.get(value,i),depth+1));
            }
            if(length>n){if(n>0)b.append(',');b.append(TRUNCATED);}
            return b.append(']').toString();
        }
        if(value instanceof Iterable<?>){
            StringBuilder b=new StringBuilder("[");
            try{
                Iterator<?> iterator=((Iterable<?>)value).iterator();
                int n=0;
                while(n<maxElements&&iterator.hasNext()){
                    if(n>0)b.append(',');
                    b.append(render(iterator.next(),depth+1));
                    n++;
                }
                if(iterator.hasNext()){if(n>0)b.append(',');b.append(TRUNCATED);}
            }catch(Throwable ignored){return limit("<"+type.getName()+":iteration-failed>");}
            return b.append(']').toString();
        }
        return limit(type.getName()+"@"+Integer.toHexString(System.identityHashCode(value)));
    }

    private String limit(String s){
        if(s.length()<=maxLength)return s;
        if(maxLength<=3)return TRUNCATED.substring(0,maxLength);
        if(maxLength<=TRUNCATED.length())return s.substring(0,maxLength-3)+"...";
        return s.substring(0,maxLength-TRUNCATED.length())+TRUNCATED;
    }
}
