package com.madlava.tracing;

/** Explicit opt-in renderer; failures are converted to a bounded marker. */
public final class ToStringArgumentRenderer implements ArgumentRenderer {
    private final int maxLength;
    public ToStringArgumentRenderer(){this(512);} public ToStringArgumentRenderer(int maxLength){this.maxLength=Math.max(1,maxLength);}
    public String render(Object value){if(value==null)return "null";try{String text=String.valueOf(value);return text.length()<=maxLength?text:text.substring(0,maxLength)+"...[truncated]";}catch(Throwable failure){return "<render-failed>";}}
}
