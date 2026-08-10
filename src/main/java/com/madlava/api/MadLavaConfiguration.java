package com.madlava.api;

import com.madlava.config.RuntimeConfigurationManager;
import com.madlava.reporting.Json;
import java.util.*;

/** Py4J-friendly runtime configuration bridge backed by the agent manager. */
public final class MadLavaConfiguration {
    private MadLavaConfiguration() { }
    public static String effectiveConfigurationJson() { RuntimeConfigurationManager manager=MadLavaRuntimeRegistry.configuration();if(manager==null)return Json.encode(Map.of("status","ERROR","reason","AGENT_UNAVAILABLE"));return Json.encode(manager.redactedValues()); }
    public static long configurationVersion() { RuntimeConfigurationManager manager=MadLavaRuntimeRegistry.configuration();return manager==null?0:manager.current().version(); }
    public static String configurationHash() { RuntimeConfigurationManager manager=MadLavaRuntimeRegistry.configuration();return manager==null?"":manager.current().hash(); }
    public static String reload() { RuntimeConfigurationManager manager=MadLavaRuntimeRegistry.configuration();if(manager==null)return Json.encode(Map.of("status","ERROR","reason","AGENT_UNAVAILABLE"));return encode(manager.reloadSource()); }
    public static String setOutputPath(String path) {
        RuntimeConfigurationManager manager=MadLavaRuntimeRegistry.configuration();
        if(manager==null)return Json.encode(Map.of("status","ERROR","reason","AGENT_UNAVAILABLE"));
        if(path==null||path.isBlank())return Json.encode(Map.of("status","REJECTED","version",manager.current().version(),"reason","INVALID_OUTPUT_DIRECTORY"));
        return encode(manager.reload(manager.current().values(),Map.of("output.directory",path),"api"));
    }
    private static String encode(RuntimeConfigurationManager.UpdateResult result){
        String reason=result.reason();
        String status=!result.applied()?"REJECTED":"UNCHANGED".equals(reason)?"UNCHANGED":"APPLIED_WITH_LISTENER_FAILURE".equals(reason)?"APPLIED_WITH_LISTENER_FAILURE":"APPLIED";
        return Json.encode(Map.of("status",status,"version",result.state().version(),"reason",reason));
    }
}
