package com.madlava.diagnostics;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AdaptiveOverheadController {
    public enum State { NORMAL, THROTTLED }
    private final double throttleThreshold,restoreThreshold;private final int consecutiveBreaches,consecutiveRecoveries;private int breaches,recoveries;private State state=State.NORMAL;private final List<Decision> decisions=new ArrayList<>();
    public AdaptiveOverheadController(double throttleThreshold,double restoreThreshold,int consecutiveBreaches,int consecutiveRecoveries){if(restoreThreshold>=throttleThreshold||consecutiveBreaches<1||consecutiveRecoveries<1)throw new IllegalArgumentException("Invalid hysteresis");this.throttleThreshold=throttleThreshold;this.restoreThreshold=restoreThreshold;this.consecutiveBreaches=consecutiveBreaches;this.consecutiveRecoveries=consecutiveRecoveries;}
    public synchronized State observe(String feature,double measured){if(state==State.NORMAL){recoveries=0;if(measured>throttleThreshold&&++breaches>=consecutiveBreaches){state=State.THROTTLED;breaches=0;decisions.add(new Decision(feature,"OVERHEAD_LIMIT",measured,throttleThreshold,"THROTTLE",Instant.now(),state));}else if(measured<=throttleThreshold)breaches=0;}else{breaches=0;if(measured<restoreThreshold&&++recoveries>=consecutiveRecoveries){state=State.NORMAL;recoveries=0;decisions.add(new Decision(feature,"OVERHEAD_RECOVERED",measured,restoreThreshold,"RESTORE",Instant.now(),state));}else if(measured>=restoreThreshold)recoveries=0;}return state;}
    public synchronized List<Decision> decisions(){return Collections.unmodifiableList(new ArrayList<>(decisions));}
    public static final class Decision{public final String feature,reason,action;public final double measured,limit;public final Instant timestamp;public final State recoveryState;private Decision(String feature,String reason,double measured,double limit,String action,Instant timestamp,State recoveryState){this.feature=feature;this.reason=reason;this.measured=measured;this.limit=limit;this.action=action;this.timestamp=timestamp;this.recoveryState=recoveryState;}}
}
