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
        if(descriptor!=null && !validMethodDescriptor(descriptor))throw new IllegalArgumentException("Invalid method descriptor: "+descriptor);
        return new MethodObservationRule(owner,method,descriptor,args?MethodObservationMode.COUNT_BY_ARGS:MethodObservationMode.COUNT,1.0);
    }

    /** Dependency-free validation of an exact JVM method descriptor. */
    static boolean validMethodDescriptor(String descriptor){
        if(descriptor==null||descriptor.length()<3||descriptor.charAt(0)!='(')return false;
        int index=1;
        while(index<descriptor.length()&&descriptor.charAt(index)!=')'){
            int next=fieldTypeEnd(descriptor,index);
            if(next<0)return false;
            index=next;
        }
        if(index>=descriptor.length()||descriptor.charAt(index)!=')')return false;
        index++;
        if(index>=descriptor.length())return false;
        if(descriptor.charAt(index)=='V')return index+1==descriptor.length();
        int end=fieldTypeEnd(descriptor,index);
        return end==descriptor.length();
    }

    private static int fieldTypeEnd(String value,int index){
        if(index>=value.length())return -1;
        char type=value.charAt(index);
        if("BCDFIJSZ".indexOf(type)>=0)return index+1;
        if(type=='['){
            int next=index;
            while(next<value.length()&&value.charAt(next)=='[')next++;
            return fieldTypeEnd(value,next);
        }
        if(type=='L'){
            int semicolon=value.indexOf(';',index+1);
            if(semicolon<=index+1)return -1;
            for(int i=index+1;i<semicolon;i++){
                char c=value.charAt(i);
                if(c=='.'||c=='['||c=='('||c==')')return -1;
            }
            return semicolon+1;
        }
        return -1;
    }
}
