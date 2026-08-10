package com.madlava.methods;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MethodRuleParserTest {
    @Test void plainRuleIsCount(){assertEquals(MethodObservationMode.COUNT, MethodRuleParser.parse("org.example.Parser.parse").mode());}
    @Test void starSuffixIsCountByArgs(){assertEquals(MethodObservationMode.COUNT_BY_ARGS, MethodRuleParser.parse("org.apache.spark.util.ClosureCleaner$.clean(*)").mode());}
    @Test void descriptorIsRetained(){assertEquals("(I)V", MethodRuleParser.parse("org.example.Parser.parse#(I)V").descriptor());}
    @Test void malformedRuleIsRejected(){assertThrows(IllegalArgumentException.class,()->MethodRuleParser.parse("Class."));}
    @Test void malformedJvmDescriptorsAreRejected(){
        assertThrows(IllegalArgumentException.class,()->MethodRuleParser.parse("org.example.Parser.parse#(I)garbage"));
        assertThrows(IllegalArgumentException.class,()->MethodRuleParser.parse("org.example.Parser.parse#(V)V"));
        assertThrows(IllegalArgumentException.class,()->MethodRuleParser.parse("org.example.Parser.parse#(I"));
        assertEquals("([Ljava/lang/String;I)Ljava/lang/Object;",MethodRuleParser.parse(
                "org.example.Parser.parse#([Ljava/lang/String;I)Ljava/lang/Object;").descriptor());
    }
}
