package com.madlava.reporting;

import com.madlava.core.FeatureState;
import java.util.Map;

public final class JsonEncoder {
    private JsonEncoder() {}
    public static String encode(Snapshot snapshot) {
        StringBuilder out = new StringBuilder(2048);
        out.append('{').append("\"schemaVersion\":3,")
           .append("\"agent\":{\"name\":\"MadLava\",\"version\":\"").append(escape(snapshot.version())).append("\",\"configurationHash\":\"").append(escape(snapshot.configurationHash())).append("\"},")
           .append("\"runtime\":{\"javaVersion\":\"").append(escape(System.getProperty("java.version", "unknown"))).append("\",\"processId\":").append(ProcessHandle.current().pid()).append("},")
           .append("\"snapshot\":{\"sequence\":").append(snapshot.sequence()).append(",\"fromTimestamp\":\"").append(snapshot.timestamp()).append("\",\"toTimestamp\":\"").append(snapshot.timestamp()).append("\",\"durationNanos\":0,\"final\":").append(snapshot.finalSnapshot()).append(",\"partial\":false,\"buildDurationNanos\":0,\"droppedPreviousSnapshotCount\":").append(snapshot.droppedCount()).append("},")
           .append("\"features\":{");
        boolean first = true;
        for (Map.Entry<String, FeatureState> entry : snapshot.features().entrySet()) {
            if (!first) out.append(','); first = false;
            out.append('"').append(escape(entry.getKey())).append("\":{\"state\":\"").append(entry.getValue()).append("\",\"featureVersion\":1,\"snapshotDurationNanos\":0,\"errorCount\":0,\"accuracy\":{\"level\":\"EXACT\",\"coveragePercent\":100.0,\"limitations\":[]},\"data\":");
            out.append(Json.encode(snapshot.featureData().getOrDefault(entry.getKey(), Map.of())));
            out.append('}');
        }
        out.append("},\"selfObservability\":{\"writerQueueCapacityCount\":64,\"droppedSnapshotCount\":").append(snapshot.droppedCount()).append("},\"artifacts\":[]}");
        return out.toString();
    }
    static String escape(String text) { StringBuilder out=new StringBuilder(text.length()+8);for(int i=0;i<text.length();i++){char c=text.charAt(i);switch(c){case '"':out.append("\\\"");break;case '\\':out.append("\\\\");break;case '\n':out.append("\\n");break;case '\r':out.append("\\r");break;case '\t':out.append("\\t");break;default:if(c<0x20)out.append(String.format("\\u%04x",(int)c));else out.append(c);}}return out.toString();}
}
