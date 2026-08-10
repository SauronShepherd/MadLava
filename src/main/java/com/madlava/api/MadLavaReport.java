package com.madlava.api;

import com.madlava.reporting.AsciiTableRenderer;
import com.madlava.reporting.Json;
import java.time.Instant;
import java.util.*;

/** Human-readable diagnostic report facade; rendering is independent of Spark. */
public final class MadLavaReport {
    private MadLavaReport() { }
    public static String reportText() { return render(MadLavaStatistics.snapshotMap(), ReportTrigger.MANUAL, StatisticsMode.CUMULATIVE, ""); }
    public static String reportSinceText(String checkpointId) { return render(MadLavaStatistics.sinceMap(checkpointId), ReportTrigger.MANUAL, StatisticsMode.CHECKPOINT_DELTA, checkpointId); }
    public static String reportMarkdown() { return fenced(reportText()); }
    public static String reportSinceMarkdown(String checkpointId) { return fenced(reportSinceText(checkpointId)); }
    public static String scopeReportText(String resultId) { MadLavaScopes.ScopeResult r=MadLavaScopes.result(resultId); if(r==null)return "ERROR: UNKNOWN_OR_EXPIRED_SCOPE_RESULT\n"; String report=render(r.statistics,ReportTrigger.SCOPE_END,StatisticsMode.SCOPE_DELTA,""); int at=report.indexOf('\n',report.indexOf("Generated At:")); String metadata="Scope: "+r.scopeName()+"\nScope ID: "+r.scopeId()+"\nResult ID: "+resultId+"\nDuration Nanos: "+r.durationNanos()+"\n"; return at<0?report+metadata:report.substring(0,at+1)+metadata+report.substring(at+1); }
    public static String scopeReportMarkdown(String resultId) { return fenced(scopeReportText(resultId)); }
    private static String render(Map<String,Object> snapshot,ReportTrigger trigger,StatisticsMode mode,String checkpoint){StringBuilder out=new StringBuilder("MadLava Runtime Report\n");out.append("Trigger: ").append(trigger).append("\nStatistics Mode: ").append(mode).append("\nGenerated At: ").append(Instant.now()).append("\n");if(!checkpoint.isEmpty())out.append("Checkpoint: ").append(singleLine(checkpoint)).append('\n');if("ERROR".equals(snapshot.get("status")))return out.append("ERROR: ").append(snapshot.get("reason")).append('\n').toString();metadata(out,snapshot,"schemaVersion","Schema Version");metadata(out,snapshot,"agentVersion","Agent Version");metadata(out,snapshot,"configurationVersion","Configuration Version");metadata(out,snapshot,"pid","JVM PID");Object features=snapshot.get("features");if(features instanceof Map<?,?>){Map<?,?> f=(Map<?,?>)features;Map<?,?> cfg=snapshot.get("effectiveConfiguration") instanceof Map<?,?>?(Map<?,?>)snapshot.get("effectiveConfiguration"):Collections.emptyMap();renderMethod(out,presentation(f.get("methodProfiling"),cfg,"methodProfiling"));renderSerialization(out,presentation(f.get("sparkSerialization"),cfg,"sparkSerialization"));}return out.toString();}
    private static Map<String,Object> presentation(Object value,Map<?,?> cfg,String section){Map<String,Object> out=new LinkedHashMap<>();if(value instanceof Map<?,?>)for(Map.Entry<?,?> e:((Map<?,?>)value).entrySet())out.put(String.valueOf(e.getKey()),e.getValue());Object global=cfg.get("reportMaxRows"), trunc=cfg.get("reportTruncate");for(String name:new String[]{"methodProfiling","argumentGroups","sparkSerialization","sparkSerializationDetail","diagnostics"}){Object specific=cfg.get("reportMaxRows."+name);out.put("_reportMaxRows."+name,specific instanceof Number?specific:global);}out.put("_reportTruncate",trunc);return out;}
    private static void metadata(StringBuilder out,Map<String,Object> snapshot,String key,String label){if(snapshot.containsKey(key))out.append(label).append(": ").append(singleLine(String.valueOf(snapshot.get(key)))).append('\n');}
    private static String singleLine(String value){StringBuilder out=new StringBuilder(value.length());for(int i=0;i<value.length();i++){char c=value.charAt(i);if(c=='\n')out.append("\\n");else if(c=='\r')out.append("\\r");else if(c=='\t')out.append("\\t");else if(c=='\u2028')out.append("\\u2028");else if(c=='\u2029')out.append("\\u2029");else if(Character.isISOControl(c))out.append('?');else out.append(c);}return out.toString();}
    private static String fenced(String value){
        int longest=0,current=0;for(int i=0;i<value.length();i++){if(value.charAt(i)=='`'){current++;longest=Math.max(longest,current);}else current=0;}
        String fence="`".repeat(Math.max(3,longest+1));return fence+"text\n"+value+fence+"\n";
    }
    private static void renderMethod(StringBuilder out,Object value){
        if(!(value instanceof Map<?,?>))return;
        Map<?,?> section=(Map<?,?>)value;Object methods=section.get("methods");if(!(methods instanceof List<?>))return;
        List<Map<?,?>> sorted=maps((List<?>)methods);sorted.sort(Comparator.comparingLong((Map<?,?> m)->number(m.get("invocations"))).reversed().thenComparing(m->String.valueOf(m.get("owner"))).thenComparing(m->String.valueOf(m.get("method"))).thenComparing(m->String.valueOf(m.get("descriptor"))));
        int methodLimit=limit(section,"methodProfiling",50);List<List<String>> rows=new ArrayList<>();for(Map<?,?> m:sorted)rows.add(List.of(methodLabel(m),String.format("%,d",number(m.get("invocations"))),String.format("%,d",number(m.get("totalDurationNanos")))));
        out.append("\nMethod Profiling\n").append(AsciiTableRenderer.render(List.of("METHOD","INVOCATIONS","TOTAL NANOS"),rows,methodLimit,truncate(section)));
        List<Map<?,?>> visible=methodLimit==0?sorted:sorted.subList(0,Math.min(methodLimit,sorted.size()));long overflow=0;
        for(Map<?,?> m:sorted)overflow+=number(m.get("overflowArgumentInvocations"));
        for(Map<?,?> m:visible){Object groups=m.get("argumentGroups");if(groups instanceof List<?>&&!((List<?>)groups).isEmpty()){List<Map<?,?>> gs=maps((List<?>)groups);gs.sort(Comparator.comparingLong((Map<?,?> g)->number(g.get("invocations"))).reversed().thenComparing(g->String.valueOf(g.get("arguments"))));List<List<String>> groupRows=new ArrayList<>();for(Map<?,?> gm:gs)groupRows.add(List.of(String.valueOf(gm.get("arguments")),String.format("%,d",number(gm.get("invocations")))));out.append("\n").append(methodLabel(m)).append(" — Argument Groups\n").append(AsciiTableRenderer.render(List.of("ARGUMENTS","INVOCATIONS"),groupRows,limit(section,"argumentGroups",50),truncate(section)));}}
        if(overflow>0)out.append("WARNING: ").append(overflow).append(" argument-group invocations overflowed the configured limit\n");
    }
    private static void renderSerialization(StringBuilder out,Object value){if(!(value instanceof Map<?,?>))return;Map<?,?> section=(Map<?,?>)value;Object groups=section.get("groups");if(!(groups instanceof List<?>))return;List<Map<?,?>> sorted=maps((List<?>)groups);sorted.sort(Comparator.comparingLong((Map<?,?> g)->number(g.get("operations"))).reversed().thenComparing(g->String.valueOf(g.get("implementation"))).thenComparing(g->String.valueOf(g.get("layer"))).thenComparing(g->String.valueOf(g.get("operation"))).thenComparing(g->String.valueOf(g.get("rootClass"))));List<List<String>> rows=new ArrayList<>();for(Map<?,?> g:sorted)rows.add(List.of(String.valueOf(g.get("implementation")),String.valueOf(g.get("layer")),String.valueOf(g.get("operation")),String.format("%,d",number(g.get("operations")))));out.append("\nSpark Serialization\n").append(AsciiTableRenderer.render(List.of("IMPLEMENTATION","LAYER","OPERATION","CALLS"),rows,limit(section,"sparkSerialization",50),truncate(section)));}
    private static String methodLabel(Map<?,?> method){Object descriptor=method.get("descriptor");return String.valueOf(method.get("owner"))+"."+method.get("method")+(descriptor==null?"":String.valueOf(descriptor));}
    private static List<Map<?,?>> maps(List<?> values){List<Map<?,?>> out=new ArrayList<>();for(Object v:values)if(v instanceof Map<?,?>)out.add((Map<?,?>)v);return out;}
    private static List<Map<?,?>> limited(List<Map<?,?>> values,int limit){return limit==0?values:values.subList(0,Math.min(limit,values.size()));}
    private static int limit(Map<?,?> section,String name,int fallback){Object config=section.get("_reportMaxRows."+name);return config instanceof Number?Math.max(0,((Number)config).intValue()):fallback;}
    private static int truncate(Map<?,?> section){Object config=section.get("_reportTruncate");return config instanceof Number?Math.max(0,((Number)config).intValue()):100;}
    private static long number(Object value){return value instanceof Number?((Number)value).longValue():0;}
}
