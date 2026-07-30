package com.madlava.reporting;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AsciiTableRendererTest {
    @Test void rendersHeadersRowsAndTruncation() {
        String table=AsciiTableRenderer.render(List.of("NAME","COUNT"),List.of(List.of("very-long-name","12"),List.of("second","3")),1,8);
        assertTrue(table.contains("NAME")); assertTrue(table.contains("very-...")); assertTrue(table.contains("1 more rows omitted"));
    }
    @Test void rendersEmptyTable() {
        String table=AsciiTableRenderer.render(List.of("NAME"),List.of(),50,100);
        assertTrue(table.startsWith("+")); assertTrue(table.contains("NAME"));
    }
}
