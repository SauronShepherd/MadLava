package com.madlava.methods;
public final class MethodObservationRule {
    private final String owner, method, descriptor; private final MethodObservationMode mode; private final double sampleRate;
    public MethodObservationRule(String owner,String method,String descriptor,MethodObservationMode mode,double sampleRate){this.owner=owner;this.method=method;this.descriptor=descriptor;this.mode=mode;this.sampleRate=sampleRate;}
    public String owner(){return owner;} public String method(){return method;} public String descriptor(){return descriptor;} public MethodObservationMode mode(){return mode;} public double sampleRate(){return sampleRate;}
}
