package com.madlava.reporting;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicBoolean;

public final class JsonlWriter implements AutoCloseable {
    private final BoundedSnapshotQueue queue; private final AtomicBoolean running=new AtomicBoolean(); private final Path path; private Thread thread;
    public JsonlWriter(BoundedSnapshotQueue queue,Path path){this.queue=queue;this.path=path;}
    public void start() throws IOException {Files.createDirectories(path.toAbsolutePath().normalize().getParent());running.set(true);thread=new Thread(this::run,"madlava-writer");thread.setDaemon(true);thread.start();}
    private void run(){try(BufferedWriter out=Files.newBufferedWriter(path,StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND)){while(running.get()||queue.size()>0){String line=queue.poll();if(line==null){try{Thread.sleep(10);}catch(InterruptedException e){Thread.currentThread().interrupt();}continue;}out.write(line);out.newLine();out.flush();}}catch(Throwable error){System.err.println("MadLava writer disabled: "+error.getClass().getSimpleName());}}
    @Override public void close(){running.set(false);if(thread!=null){try{thread.join(3000);}catch(InterruptedException e){Thread.currentThread().interrupt();}}}
}
