package com.madlava.tracing;

import java.util.concurrent.ThreadLocalRandom;

public final class TraceSampler {
    private final double rate;
    public TraceSampler(double rate){if(Double.isNaN(rate)||rate<0||rate>1)throw new IllegalArgumentException("sampleRate must be between 0 and 1");this.rate=rate;}
    public boolean sample(){return rate>=1 || rate>0 && ThreadLocalRandom.current().nextDouble()<rate;}
    public double rate(){return rate;}
}
