package com.madlava.reporting;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicBoolean;
import java.security.MessageDigest;

public final class JsonlWriter implements AutoCloseable {
    private static final long MAX_SEGMENT_BYTES = 1_048_576L;
    private final BoundedSnapshotQueue queue; private final AtomicBoolean running=new AtomicBoolean(); private final Path path; private Thread thread;
    private volatile Path lastSegment;
    public JsonlWriter(BoundedSnapshotQueue queue,Path path){this.queue=queue;this.path=path;}
    public void start() throws IOException {Files.createDirectories(path.toAbsolutePath().normalize().getParent());running.set(true);thread=new Thread(this::run,"madlava-writer");thread.setDaemon(true);thread.start();}
    private void run(){
        BufferedWriter out=null;
        try {
            Path segments=path.resolveSibling("segments"); Files.createDirectories(segments);
            int index=1; Path partial=path;
            out=Files.newBufferedWriter(partial,StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND); lastSegment=partial;
            while(running.get()||queue.size()>0){
                String line=queue.poll();
                if(line==null){try{Thread.sleep(10);}catch(InterruptedException e){Thread.currentThread().interrupt();}continue;}
                byte[] encoded=(line+System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
                if(Files.size(partial)>0 && Files.size(partial)+encoded.length>MAX_SEGMENT_BYTES){
                    out.flush(); out.close();
                    Path finalized=segments.resolve(String.format("segment-%06d.jsonl",index++));
                    Files.move(partial,finalized,java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                    partial=segments.resolve(String.format("segment-%06d.partial",index));
                    out=Files.newBufferedWriter(partial,StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND); lastSegment=partial;
                }
                out.write(line); out.newLine(); out.flush();
            }
            if(out!=null){out.flush();out.close();}
            if(lastSegment!=null && lastSegment.getFileName().toString().endsWith(".partial")){
                Path finalized=lastSegment.resolveSibling(lastSegment.getFileName().toString().replace(".partial",".jsonl"));
                Files.move(lastSegment,finalized,java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            }
        } catch(Throwable error){try{if(out!=null)out.close();}catch(IOException ignored){}System.err.println("MadLava writer disabled: "+error.getClass().getSimpleName());}
    }
    @Override public void close(){
        running.set(false);
        if(thread!=null){try{thread.join(3000);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
        finalizeManifest();
    }

    private void finalizeManifest() {
        try {
            Path manifestTarget = path;
            if (!Files.exists(manifestTarget)) {
                Path segments = path.resolveSibling("segments");
                try (java.util.stream.Stream<Path> files = Files.list(segments)) {
                    manifestTarget = files.filter(p -> p.getFileName().toString().endsWith(".jsonl"))
                            .max(java.util.Comparator.comparing(p -> p.getFileName().toString()))
                            .orElse(path);
                }
            }
            byte[] bytes = Files.readAllBytes(manifestTarget);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) hex.append(String.format("%02x", value));
            long records;
            try (java.util.stream.Stream<Path> files = Files.list(path.resolveSibling("segments"))) {
                records = files.filter(p -> p.getFileName().toString().endsWith(".jsonl"))
                        .mapToLong(JsonlWriter::lineCount).sum();
            }
            if (records == 0) records = Files.lines(manifestTarget, StandardCharsets.UTF_8).count();
            String manifest = "{\"state\":\"FINAL\",\"path\":\"" + escape(manifestTarget.toString())
                    + "\",\"records\":" + records + ",\"bytes\":" + bytes.length
                    + ",\"sha256\":\"" + hex + "\"}\n";
            Files.writeString(path.resolveSibling("madlava-report-manifest.json"), manifest,
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Throwable ignored) {
            // Integrity metadata must never affect application shutdown.
        }
    }

    private static long lineCount(Path file) {
        try (java.util.stream.Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) { return lines.count(); }
        catch (IOException ignored) { return 0; }
    }

    private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
