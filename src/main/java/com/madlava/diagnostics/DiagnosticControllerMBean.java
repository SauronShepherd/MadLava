package com.madlava.diagnostics;

public interface DiagnosticControllerMBean {
    String getState();
    String getEffectiveConfiguration();
    String reloadConfiguration(String candidate);
    String triggerThreadDump();
    String triggerHeapDump();
}
