package com.madlava.config;

import java.io.Closeable;
import java.nio.file.*;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

/** Debounced daemon watcher for replace-in-place and atomic-replace editor saves. */
public final class ConfigurationWatcher implements Closeable {
    private final Path file; private final RuntimeConfigurationManager manager; private final WatchService service; private WatchKey watchKey;
    private final AtomicBoolean running = new AtomicBoolean(); private Thread thread;
    public ConfigurationWatcher(Path file, RuntimeConfigurationManager manager) throws Exception {
        this.file=file.toAbsolutePath().normalize(); this.manager=manager; this.service=FileSystems.getDefault().newWatchService();
        this.watchKey=this.file.getParent().register(service, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
    }
    public void start() { if (!running.compareAndSet(false,true)) return; if (Files.isRegularFile(file)) manager.reloadJson(file, Collections.emptyMap()); thread=new Thread(this::run,"madlava-config-watcher");thread.setDaemon(true);thread.start(); }
    private void run() { while(running.get()) { try { WatchKey key=service.take(); boolean changed=false; for(WatchEvent<?> event:key.pollEvents()){Path name=(Path)event.context();if(file.getFileName().equals(name))changed=true;} key.reset(); if(changed){Thread.sleep(100); if(Files.isRegularFile(file)) manager.reloadJson(file, Collections.emptyMap());} } catch(InterruptedException e){Thread.currentThread().interrupt();} catch(Exception ignored){} } }
    public void close(){running.set(false);if(thread!=null)thread.interrupt();if(watchKey!=null)watchKey.cancel();try{service.close();}catch(Exception ignored){}}
}
