package com.madlava.api;

import com.madlava.config.RuntimeConfigurationManager;
import com.madlava.reporting.Json;
import java.util.*;

/** Py4J-friendly runtime configuration bridge backed by the agent manager. */
public final class MadLavaConfiguration {
    private MadLavaConfiguration() { }
    public static String effectiveConfigurationJson() { RuntimeConfigurationManager manager=MadLavaRuntimeRegistry.configuration();if(manager==null)return Json.encode(Map.of("status","ERROR","reason","AGENT_UNAVAILABLE"));return Json.encode(manager.current().values()); }
    public static long configurationVersion() { RuntimeConfigurationManager manager=MadLavaRuntimeRegistry.configuration();return manager==null?0:manager.current().version(); }
    public static String configurationHash() { RuntimeConfigurationManager manager=MadLavaRuntimeRegistry.configuration();return manager==null?"":manager.current().hash(); }
    public static String reload() { RuntimeConfigurationManager manager=MadLavaRuntimeRegistry.configuration();if(manager==null)return Json.encode(Map.of("status","ERROR","reason","AGENT_UNAVAILABLE"));RuntimeConfigurationManager.UpdateResult result=manager.reloadSource();return Json.encode(Map.of("status",result.applied()?"APPLIED":"REJECTED","version",result.state().version(),"reason",result.reason())); }
    public static String setOutputPath(String path) { RuntimeConfigurationManager manager=MadLavaRuntimeRegistry.configuration();if(manager==null)return Json.encode(Map.of("status","ERROR","reason","AGENT_UNAVAILABLE"));RuntimeConfigurationManager.UpdateResult result=manager.reload(manager.current().values(),Map.of("reporting.output",path),"api");return Json.encode(Map.of("status",result.applied()?"APPLIED":"REJECTED","version",result.state().version(),"reason",result.reason())); }
}
