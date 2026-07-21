package com.madlava.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public final class EffectiveConfiguration {
    private final Map<String,Object> values; private final String source; private final String hash;
    public EffectiveConfiguration(Map<String,Object> values,String source){this.values=Collections.unmodifiableMap(new TreeMap<>(values));this.source=source;this.hash=hash(this.values);}
    public Map<String,Object> values(){return values;} public String source(){return source;} public String hash(){return hash;}
    public Map<String,Object> redacted(ConfigurationMetadata metadata){Map<String,Object> copy=new TreeMap<>(values);metadata.entries().values().stream().filter(ConfigurationMetadata.Entry::secret).forEach(e->{if(copy.containsKey(e.path()))copy.put(e.path(),"<redacted>");});return Collections.unmodifiableMap(copy);}
    private static String hash(Map<String,Object> values){try{MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] bytes=digest.digest(values.toString().getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format("%02x",b));return out.toString();}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
}
