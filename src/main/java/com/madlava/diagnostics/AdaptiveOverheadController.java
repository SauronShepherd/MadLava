package com.madlava.diagnostics;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AdaptiveOverheadController {
    public enum State { NORMAL, THROTTLED }
    private static final int MAX_DECISIONS=256;
    private final double throttleThreshold,restoreThreshold;private final int consecutiveBreaches,consecutiveRecoveries;private int breaches,recoveries;private State state=State.NORMAL;private final ArrayDeque<Decision> decisions=new ArrayDeque<>();private long droppedDecisions;
    public AdaptiveOverheadController(double throttleThreshold,double restoreThreshold,int consecutiveBreaches,int consecutiveRecoveries){if(!Double.isFinite(throttleThreshold)||!Double.isFinite(restoreThreshold)||restoreThreshold<0||throttleThreshold<0||restoreThreshold>=throttleThreshold||consecutiveBreaches<1||consecutiveRecoveries<1)throw new IllegalArgumentException("Invalid hysteresis");this.throttleThreshold=throttleThreshold;this.restoreThreshold=restoreThreshold;this.consecutiveBreaches=consecutiveBreaches;this.consecutiveRecoveries=consecutiveRecoveries;}
    public synchronized State observe(String feature,double measured){if(!Double.isFinite(measured)){breaches=0;recoveries=0;return state;}if(state==State.NORMAL){recoveries=0;if(measured>throttleThreshold&&++breaches>=consecutiveBreaches){state=State.THROTTLED;breaches=0;record(new Decision(feature,"OVERHEAD_LIMIT",measured,throttleThreshold,"THROTTLE",Instant.now(),state));}else if(measured<=throttleThreshold)breaches=0;}else{breaches=0;if(measured<restoreThreshold&&++recoveries>=consecutiveRecoveries){state=State.NORMAL;recoveries=0;record(new Decision(feature,"OVERHEAD_RECOVERED",measured,restoreThreshold,"RESTORE",Instant.now(),state));}else if(measured>=restoreThreshold)recoveries=0;}return state;}
    private void record(Decision decision){if(decisions.size()>=MAX_DECISIONS){decisions.removeFirst();droppedDecisions++;}decisions.addLast(decision);}
    public synchronized List<Decision> decisions(){return Collections.unmodifiableList(new ArrayList<>(decisions));}
    public synchronized long droppedDecisions(){return droppedDecisions;}
    public static final class Decision{public final String feature,reason,action;public final double measured,limit;public final Instant timestamp;public final State recoveryState;private Decision(String feature,String reason,double measured,double limit,String action,Instant timestamp,State recoveryState){this.feature=feature;this.reason=reason;this.measured=measured;this.limit=limit;this.action=action;this.timestamp=timestamp;this.recoveryState=recoveryState;}}
}
