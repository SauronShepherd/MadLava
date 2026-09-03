package com.madlava.config;

import java.nio.ByteBuffer;
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

    /**
     * Hash an unambiguous typed/length-prefixed representation.
     * Map.toString() is not a canonical serialization: string values containing delimiters and
     * adjacent key names can make two different maps produce identical text.
     */
    private static String hash(Map<String,Object> values){
        try{
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            for(Map.Entry<String,Object> entry:values.entrySet()){
                update(digest, "K", entry.getKey());
                Object value=entry.getValue();
                if(value instanceof String) update(digest,"S",(String)value);
                else if(value instanceof Boolean) update(digest,"B",Boolean.toString((Boolean)value));
                else if(value instanceof Byte||value instanceof Short||value instanceof Integer||value instanceof Long)
                    update(digest,"I",Long.toString(((Number)value).longValue()));
                else if(value instanceof Float||value instanceof Double)
                    update(digest,"N",Double.toString(((Number)value).doubleValue()));
                else if(value==null) update(digest,"0","");
                else update(digest,"T",value.getClass().getName());
            }
            StringBuilder out=new StringBuilder();for(byte b:digest.digest())out.append(String.format("%02x",b));return out.toString();
        }catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static void update(MessageDigest digest,String type,String text){
        byte[] typeBytes=type.getBytes(StandardCharsets.UTF_8);
        byte[] bytes=text.getBytes(StandardCharsets.UTF_8);
        digest.update((byte)typeBytes.length);digest.update(typeBytes);
        digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());digest.update(bytes);
    }
}
