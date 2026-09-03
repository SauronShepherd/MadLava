package com.madlava.tracing;

import java.util.*;

/** Central bounded, fail-open argument capture policy for TRACE_ARGS. */
public final class ArgumentCapture {
    private final ArgumentRenderer renderer; private final ArgumentRedactor redactor; private final int maxArguments;
    public ArgumentCapture(ArgumentRenderer renderer, ArgumentRedactor redactor, int maxArguments) {
        if(renderer==null||redactor==null||maxArguments<0)throw new IllegalArgumentException();
        this.renderer=renderer;this.redactor=redactor;this.maxArguments=maxArguments;
    }
    public List<String> capture(Object[] arguments) {
        if(arguments==null||maxArguments==0)return List.of();
        int count=Math.min(arguments.length,maxArguments); List<String> result=new ArrayList<>(count);
        for(int i=0;i<count;i++){String rendered;try{rendered=renderer.render(arguments[i]);}catch(Throwable ignored){rendered="<render-failed>";}result.add(redactor.redact(i,rendered));}
        return Collections.unmodifiableList(result);
    }
}
