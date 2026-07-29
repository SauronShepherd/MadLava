package com.madlava.api;

import com.madlava.reporting.AgentRuntime;
import com.madlava.config.RuntimeConfigurationManager;
import java.util.concurrent.atomic.AtomicReference;

public final class MadLavaRuntimeRegistry {
    private static final AtomicReference<AgentRuntime> ACTIVE = new AtomicReference<>();
    private static final AtomicReference<RuntimeConfigurationManager> CONFIGURATION = new AtomicReference<>();
    private MadLavaRuntimeRegistry() { }
    public static boolean register(AgentRuntime runtime) { return runtime != null && ACTIVE.compareAndSet(null, runtime); }
    public static void clear(AgentRuntime runtime) { ACTIVE.compareAndSet(runtime, null); }
    public static void registerConfiguration(RuntimeConfigurationManager manager) { CONFIGURATION.set(manager); }
    public static void clearConfiguration(RuntimeConfigurationManager manager) { CONFIGURATION.compareAndSet(manager, null); }
    public static RuntimeConfigurationManager configuration() { return CONFIGURATION.get(); }
    public static AgentRuntime current() { return ACTIVE.get(); }
}
