package com.madlava.methods;

import java.util.*;

/** Immutable compiled lookup for method observation modes. */
public final class MethodObservationPlan {
    private final List<MethodObservationRule> rules;
    private MethodObservationPlan(List<MethodObservationRule> rules){this.rules=Collections.unmodifiableList(new ArrayList<>(rules));}

    public static MethodObservationPlan compile(Iterable<String> sources){
        List<MethodObservationRule> rules=new ArrayList<>();
        if(sources!=null)for(String source:sources)if(source!=null&&!source.isBlank()){
            String trimmed=source.trim();
            String identity=identityWithoutObservationSuffix(trimmed);
            // Wildcard filter rules belong to MethodFilter, not the exact observation plan.
            if(identity.indexOf('*')>=0||identity.indexOf('?')>=0)continue;
            rules.add(MethodRuleParser.parse(trimmed));
        }
        return new MethodObservationPlan(rules);
    }

    private static String identityWithoutObservationSuffix(String source){
        int hash=source.indexOf('#');
        String identity=hash>=0?source.substring(0,hash):source;
        if(identity.endsWith("(*)"))identity=identity.substring(0,identity.length()-3);
        return identity;
    }

    public static MethodObservationPlan empty(){return new MethodObservationPlan(Collections.emptyList());}

    public Optional<MethodObservationRule> find(String owner,String method,String descriptor){
        MethodObservationRule generic=null;
        for(MethodObservationRule rule:rules){
            if(!rule.owner().equals(owner)||!rule.method().equals(method))continue;
            if(rule.descriptor()!=null&&rule.descriptor().equals(descriptor))return Optional.of(rule);
            if(rule.descriptor()==null&&generic==null)generic=rule;
        }
        return Optional.ofNullable(generic);
    }
    public List<MethodObservationRule> rules(){return rules;}
}
