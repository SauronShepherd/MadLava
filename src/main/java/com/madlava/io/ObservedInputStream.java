package com.madlava.io;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class ObservedInputStream extends FilterInputStream {
    private final String layer;
    public ObservedInputStream(InputStream delegate,String layer){super(delegate);this.layer=layer;}
    @Override public int read() throws IOException{try{int value=super.read();RuntimeObservationBridge.io("read",layer,value<0?-1:1,true);return value;}catch(IOException failure){RuntimeObservationBridge.io("read",layer,0,false);throw failure;}}
    @Override public int read(byte[] buffer,int offset,int length)throws IOException{try{int value=super.read(buffer,offset,length);RuntimeObservationBridge.io("read",layer,value,true);return value;}catch(IOException failure){RuntimeObservationBridge.io("read",layer,0,false);throw failure;}}
}
