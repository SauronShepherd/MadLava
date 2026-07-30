package com.madlava.tracing;

import java.lang.reflect.Array;
import java.util.*;

/** Identity-free, bounded grouping representation for COUNT_BY_ARGS. */
public final class ArgumentCanonicalizer {
    private final int maxStringLength;
    public ArgumentCanonicalizer(){this(256);} public ArgumentCanonicalizer(int maxStringLength){this.maxStringLength=Math.max(1,maxStringLength);}
    public List<String> canonicalize(Object[] values){if(values==null)return List.of();List<String> result=new ArrayList<>(values.length);for(Object value:values)result.add(canonicalize(value));return Collections.unmodifiableList(result);}
    public String canonicalize(Object value){if(value==null)return "null";if(value instanceof String||value instanceof CharSequence||value instanceof Boolean||value instanceof Number||value instanceof Character)return limit(String.valueOf(value));if(value instanceof Enum<?>)return value.getClass().getName()+"."+((Enum<?>)value).name();if(value instanceof Class<?>)return ((Class<?>)value).getName();Class<?> type=value.getClass();if(type.isArray())return type.getComponentType().getName()+"["+Array.getLength(value)+"]";return canonicalizeClassName(type.getName());}
    public String canonicalizeClassName(String name){if(name==null||name.isBlank())return "<unavailable>";int hidden=name.indexOf("/0x");if(hidden>=0)name=name.substring(0,hidden);int identity=name.lastIndexOf('@');if(identity>0)name=name.substring(0,identity);return name;}
    private String limit(String value){return value.length()<=maxStringLength?value:value.substring(0,maxStringLength)+"...[truncated]";}
}
