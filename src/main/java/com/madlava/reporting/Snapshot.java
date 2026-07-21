package com.madlava.reporting;

import com.madlava.core.FeatureState;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Snapshot {
    private final String version, configurationHash; private final long sequence, droppedCount; private final Instant timestamp; private final boolean finalSnapshot; private final Map<String, FeatureState> features; private final Map<String,Object> featureData;
    public Snapshot(String version,String configurationHash,long sequence,long droppedCount,Instant timestamp,boolean finalSnapshot,Map<String,FeatureState> features){this(version,configurationHash,sequence,droppedCount,timestamp,finalSnapshot,features,Map.of());}
    public Snapshot(String version,String configurationHash,long sequence,long droppedCount,Instant timestamp,boolean finalSnapshot,Map<String,FeatureState> features,Map<String,Object> featureData){this.version=version;this.configurationHash=configurationHash;this.sequence=sequence;this.droppedCount=droppedCount;this.timestamp=timestamp;this.finalSnapshot=finalSnapshot;this.features=Collections.unmodifiableMap(new LinkedHashMap<>(features));this.featureData=Collections.unmodifiableMap(new LinkedHashMap<>(featureData));}
    public String version(){return version;} public String configurationHash(){return configurationHash;} public long sequence(){return sequence;} public long droppedCount(){return droppedCount;} public Instant timestamp(){return timestamp;} public boolean finalSnapshot(){return finalSnapshot;} public Map<String,FeatureState> features(){return features;} public Map<String,Object> featureData(){return featureData;}
}
