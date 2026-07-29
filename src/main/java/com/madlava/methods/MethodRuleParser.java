package com.madlava.methods;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MethodRuleParser {
    private static final Pattern RULE=Pattern.compile("^([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*)\\.([A-Za-z_$][\\w$]*)(?:\\(\\*\\))?(?:#(.+))?$");
    private MethodRuleParser(){}
    public static MethodObservationRule parse(String source){
        if(source==null)throw new IllegalArgumentException("Method rule is null"); Matcher m=RULE.matcher(source.trim());
        if(!m.matches())throw new IllegalArgumentException("Invalid method rule: "+source);
        boolean args=source.trim().contains("(*)"); String owner=m.group(1); String method=m.group(2); String descriptor=m.group(3);
        if(descriptor!=null && (descriptor.isBlank() || !descriptor.startsWith("(")))throw new IllegalArgumentException("Invalid method descriptor: "+descriptor);
        return new MethodObservationRule(owner,method,descriptor,args?MethodObservationMode.COUNT_BY_ARGS:MethodObservationMode.COUNT,1.0);
    }
}
