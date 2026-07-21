package com.madlava.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;

public final class ObservedByteChannel implements ByteChannel {
    private final ByteChannel delegate; private final String layer;
    public ObservedByteChannel(ByteChannel delegate,String layer){this.delegate=delegate;this.layer=layer;}
    @Override public int read(ByteBuffer target)throws IOException{try{int actual=delegate.read(target);RuntimeObservationBridge.io("channel-read",layer,actual,true);return actual;}catch(IOException failure){RuntimeObservationBridge.io("channel-read",layer,0,false);throw failure;}}
    @Override public int write(ByteBuffer source)throws IOException{try{int actual=delegate.write(source);RuntimeObservationBridge.io("channel-write",layer,actual,true);return actual;}catch(IOException failure){RuntimeObservationBridge.io("channel-write",layer,0,false);throw failure;}}
    @Override public boolean isOpen(){return delegate.isOpen();}
    @Override public void close()throws IOException{delegate.close();}
}
