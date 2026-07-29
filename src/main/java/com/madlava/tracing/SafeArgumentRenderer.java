package com.madlava.tracing;

import java.util.*;

/** Bounded renderer that never calls application toString() for arbitrary objects. */
public final class SafeArgumentRenderer implements ArgumentRenderer {
    private final int maxLength, maxElements;
    public SafeArgumentRenderer(){this(512,16);} public SafeArgumentRenderer(int maxLength,int maxElements){this.maxLength=Math.max(1,maxLength);this.maxElements=Math.max(1,maxElements);}
    public String render(Object value){return render(value,0);}
    private String render(Object value,int depth){if(value==null)return "null"; if(value instanceof String||value instanceof Number||value instanceof Boolean||value instanceof Character||value instanceof Enum||value instanceof UUID||value instanceof Class)return limit(String.valueOf(value)); if(depth>1)return "<"+value.getClass().getName()+">"; if(value.getClass().isArray()){StringBuilder b=new StringBuilder("[");int n=Math.min(java.lang.reflect.Array.getLength(value),maxElements);for(int i=0;i<n;i++){if(i>0)b.append(',');b.append(render(java.lang.reflect.Array.get(value,i),depth+1));}return b.append(']').toString();} if(value instanceof Iterable<?>){StringBuilder b=new StringBuilder("[");int n=0;for(Object item:(Iterable<?>)value){if(n++>=maxElements)break;if(n>1)b.append(',');b.append(render(item,depth+1));}return b.append(']').toString();} return value.getClass().getName()+"@"+Integer.toHexString(System.identityHashCode(value));}
    private String limit(String s){return s.length()<=maxLength?s:s.substring(0,maxLength)+"...[truncated]";}
}
