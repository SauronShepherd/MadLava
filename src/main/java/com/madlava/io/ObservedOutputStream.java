package com.madlava.io;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public final class ObservedOutputStream extends FilterOutputStream {
    private final String layer;
    public ObservedOutputStream(OutputStream delegate,String layer){super(delegate);this.layer=layer;}
    @Override public void write(int value)throws IOException{try{out.write(value);RuntimeObservationBridge.io("write",layer,1,true);}catch(IOException failure){RuntimeObservationBridge.io("write",layer,0,false);throw failure;}}
    @Override public void write(byte[] buffer,int offset,int length)throws IOException{try{out.write(buffer,offset,length);RuntimeObservationBridge.io("write",layer,length,true);}catch(IOException failure){RuntimeObservationBridge.io("write",layer,0,false);throw failure;}}
}
