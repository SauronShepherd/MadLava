package com.madlava.reporting;
import java.util.*;
public final class AsciiTableRenderer {
    private AsciiTableRenderer() { }
    public static String render(List<String> headers,List<List<String>> rows,int maxRows,int truncate){List<List<String>> limited=new ArrayList<>();int count=maxRows==0?rows.size():Math.min(maxRows,rows.size());for(int i=0;i<count;i++){List<String> row=new ArrayList<>();for(String value:rows.get(i))row.add(shorten(value,truncate));limited.add(row);}int[] widths=new int[headers.size()];for(int i=0;i<widths.length;i++)widths[i]=headers.get(i).length();for(List<String> row:limited)for(int i=0;i<Math.min(widths.length,row.size());i++)widths[i]=Math.max(widths[i],row.get(i).length());StringBuilder out=new StringBuilder();String border=border(widths);out.append(border).append(line(headers,widths)).append(border);for(List<String> row:limited)out.append(line(row,widths));out.append(border);if(rows.size()>count)out.append("... ").append(rows.size()-count).append(" more rows omitted\n");return out.toString();}
    private static String border(int[] widths){StringBuilder b=new StringBuilder("+");for(int width:widths)b.append("-").append("-".repeat(width)).append("-+");return b.append('\n').toString();}
    private static String line(List<String> values,int[] widths){StringBuilder b=new StringBuilder("|");for(int i=0;i<widths.length;i++){String value=i<values.size()?values.get(i):"";b.append(' ').append(value).append(" ".repeat(widths[i]-value.length()+1)).append('|');}return b.append('\n').toString();}
    private static String shorten(String value,int limit){if(value==null)return "";return value.length()<=limit?value:value.substring(0,Math.max(0,limit-3))+"...";}
}
