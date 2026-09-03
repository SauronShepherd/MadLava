package com.madlava.tracing;

/** Explicit opt-in renderer; failures are converted to a bounded marker. */
public final class ToStringArgumentRenderer implements ArgumentRenderer {
    private final int maxLength;
    public ToStringArgumentRenderer(){this(512);} public ToStringArgumentRenderer(int maxLength){this.maxLength=Math.max(1,maxLength);}
    public String render(Object value){if(value==null)return "null";try{return limit(String.valueOf(value));}catch(Throwable failure){return limit("<render-failed>");}}
    private String limit(String text){
        if(text.length()<=maxLength)return text;
        final String suffix="...[truncated]";
        if(maxLength<=suffix.length())return text.substring(0,maxLength);
        return text.substring(0,maxLength-suffix.length())+suffix;
    }
}
