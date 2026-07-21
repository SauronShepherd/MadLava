package com.madlava.diagnostics;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class IncidentRecorder {
    private final int capacity;private final ArrayDeque<Incident> incidents=new ArrayDeque<>();private long dropped;
    public IncidentRecorder(int capacity){if(capacity<1)throw new IllegalArgumentException("capacity");this.capacity=capacity;}
    public synchronized void record(String feature,String category,String action){if(incidents.size()==capacity){incidents.removeFirst();dropped++;}incidents.addLast(new Incident(feature,category,action,Instant.now()));}
    public synchronized Snapshot snapshot(){return new Snapshot(Collections.unmodifiableList(new ArrayList<>(incidents)),dropped);}
    public static final class Incident{public final String feature,category,action;public final Instant timestamp;private Incident(String feature,String category,String action,Instant timestamp){this.feature=feature;this.category=category;this.action=action;this.timestamp=timestamp;}}
    public static final class Snapshot{public final List<Incident> incidents;public final long dropped;private Snapshot(List<Incident> incidents,long dropped){this.incidents=incidents;this.dropped=dropped;}}
}
