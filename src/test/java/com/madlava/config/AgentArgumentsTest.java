package com.madlava.config;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class AgentArgumentsTest {
 @Test void parsesQuotedCommaAndScalarValues(){var values=AgentArguments.parse("config=madlava.json,tag=\"one,two\",enabled=true");assertEquals("one,two",values.get("tag"));assertEquals("true",values.get("enabled"));}
 @Test void rejectsDuplicatesAndMalformedInput(){assertThrows(IllegalArgumentException.class,()->AgentArguments.parse("a=1,a=2"));assertThrows(IllegalArgumentException.class,()->AgentArguments.parse("missing"));assertThrows(IllegalArgumentException.class,()->AgentArguments.parse("a=\"open"));}
}
