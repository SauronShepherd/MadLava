package com.madlava.tracing;

import java.util.*;
import java.util.regex.Pattern;

public final class ArgumentRedactor {
    private final Set<Integer> indexes; private final List<Pattern> patterns;
    public ArgumentRedactor(Collection<Integer> indexes,Collection<String> patterns){this.indexes=Collections.unmodifiableSet(new HashSet<>(indexes==null?Collections.emptySet():indexes));List<Pattern> compiled=new ArrayList<>();if(patterns!=null)for(String p:patterns)compiled.add(Pattern.compile(p));this.patterns=Collections.unmodifiableList(compiled);}
    public String redact(int index,String rendered){if(indexes.contains(index)||patterns.stream().anyMatch(p->p.matcher(rendered).matches()))return "<redacted>";return rendered;}
}
