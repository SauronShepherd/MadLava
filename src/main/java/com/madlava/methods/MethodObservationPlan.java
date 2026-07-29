package com.madlava.methods;

import java.util.*;

/** Immutable compiled lookup for method observation modes. */
public final class MethodObservationPlan {
    private final List<MethodObservationRule> rules;
    private MethodObservationPlan(List<MethodObservationRule> rules){this.rules=Collections.unmodifiableList(new ArrayList<>(rules));}
    public static MethodObservationPlan compile(Iterable<String> sources){List<MethodObservationRule> rules=new ArrayList<>();if(sources!=null)for(String source:sources)if(source!=null&&!source.isBlank())rules.add(MethodRuleParser.parse(source));return new MethodObservationPlan(rules);}
    public static MethodObservationPlan empty(){return new MethodObservationPlan(Collections.emptyList());}
    public Optional<MethodObservationRule> find(String owner,String method,String descriptor){return rules.stream().filter(rule->rule.owner().equals(owner)&&rule.method().equals(method)&&(rule.descriptor()==null||rule.descriptor().equals(descriptor))).findFirst();}
    public List<MethodObservationRule> rules(){return rules;}
}
