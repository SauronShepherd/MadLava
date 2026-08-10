package com.madlava.config;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ConfigurationResolver {
    private final ConfigurationMetadata metadata;
    public ConfigurationResolver(ConfigurationMetadata metadata){this.metadata=metadata;}
    public EffectiveConfiguration resolve(Map<String,?> fileValues,Map<String,String> overrides,String source){
        Map<String,Object> result=new LinkedHashMap<>();
        metadata.entries().values().forEach(e->result.put(e.path(),e.defaultValue()));
        if(fileValues!=null)fileValues.forEach((key,value)->apply(result,key,value));
        if(overrides!=null)overrides.forEach((key,value)->apply(result,key,coerce(key,value)));
        // Section limits inherit the *effective* global human row limit unless the section was
        // explicitly set in the file or compact overrides. Materializing metadata defaults first
        // used to turn an omitted section into 50 and silently defeat that inheritance.
        Object global=result.get("reporting.human.maxRows");
        for(String section:new String[]{"methodProfiling","argumentGroups","sparkSerialization","sparkSerializationDetail","diagnostics"}){
            String key="reporting.human.sections."+section+".maxRows";
            boolean explicit=(fileValues!=null&&fileValues.containsKey(key))||(overrides!=null&&overrides.containsKey(key));
            if(!explicit)result.put(key,global);
        }
        return new EffectiveConfiguration(result,source==null?"embedded":source);
    }
    private Object coerce(String key,String value){ConfigurationMetadata.Entry e=required(key);try{switch(e.type()){case BOOLEAN:if(!value.equalsIgnoreCase("true")&&!value.equalsIgnoreCase("false"))throw new IllegalArgumentException();return Boolean.parseBoolean(value);case INTEGER:return Integer.valueOf(value);case NUMBER:return Double.valueOf(value);default:return value;}}catch(RuntimeException error){throw new IllegalArgumentException("Invalid value for "+key,error);}}
    private void apply(Map<String,Object> result,String key,Object value){ConfigurationMetadata.Entry e=required(key);if(!matches(e.type(),value))throw new IllegalArgumentException("Wrong type for "+key);if(value instanceof Number){double n=((Number)value).doubleValue();if(!Double.isFinite(n))throw new IllegalArgumentException("Non-finite value: "+key);if(e.minimum()!=null&&n<e.minimum()||e.maximum()!=null&&n>e.maximum())throw new IllegalArgumentException("Out of range: "+key);}if("output.directory".equals(key)&&((String)value).isBlank())throw new IllegalArgumentException("Output directory cannot be blank");result.put(key,value);}
    public Map<String,Object> redacted(Map<String,?> values){Map<String,Object> copy=new LinkedHashMap<>();if(values!=null)values.forEach(copy::put);metadata.entries().values().stream().filter(ConfigurationMetadata.Entry::secret).forEach(e->{if(copy.containsKey(e.path()))copy.put(e.path(),"<redacted>");});return java.util.Collections.unmodifiableMap(copy);}
    private ConfigurationMetadata.Entry required(String key){ConfigurationMetadata.Entry e=metadata.entries().get(key);if(e==null)throw new IllegalArgumentException("Unknown configuration property: "+key);return e;}
    private static boolean matches(ConfigurationMetadata.Type type,Object value){if(value==null)return false;switch(type){case BOOLEAN:return value instanceof Boolean;case INTEGER:return value instanceof Byte||value instanceof Short||value instanceof Integer||value instanceof Long;case NUMBER:return value instanceof Number;default:return value instanceof String;}}
}
