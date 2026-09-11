package com.madlava.reporting;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Single-consumer JSONL writer with bounded segment rotation and bounded flush latency. */
public final class JsonlWriter implements AutoCloseable {
    private static final long DEFAULT_MAX_SEGMENT_BYTES = 100L * 1024L * 1024L;
    private static final int DEFAULT_FLUSH_RECORDS = 64;
    private static final long DEFAULT_FLUSH_MILLIS = 1000L;
    private final BoundedSnapshotQueue queue;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicLong manifestFinalizations = new AtomicLong();
    private final long maxSegmentBytes;
    private final int flushRecords;
    private final long flushNanos;
    private volatile boolean drainOnStop = true;
    private volatile Path path;
    private Thread thread;
    private long generation;
    private long finalizedGeneration = -1L;

    public JsonlWriter(BoundedSnapshotQueue queue, Path path) {
        this(queue, path, DEFAULT_MAX_SEGMENT_BYTES,
                positiveIntProperty("madlava.writer.flushRecords", DEFAULT_FLUSH_RECORDS),
                positiveLongProperty("madlava.writer.flushMillis", DEFAULT_FLUSH_MILLIS));
    }

    JsonlWriter(BoundedSnapshotQueue queue, Path path, long maxSegmentBytes) {
        this(queue, path, maxSegmentBytes, DEFAULT_FLUSH_RECORDS, DEFAULT_FLUSH_MILLIS);
    }

    JsonlWriter(BoundedSnapshotQueue queue, Path path, long maxSegmentBytes, int flushRecords, long flushMillis) {
        if (queue == null || path == null || maxSegmentBytes < 1L || flushRecords < 1 || flushMillis < 1L) throw new IllegalArgumentException();
        this.queue = queue;
        this.path = path;
        this.maxSegmentBytes = maxSegmentBytes;
        this.flushRecords = flushRecords;
        this.flushNanos = TimeUnit.MILLISECONDS.toNanos(flushMillis);
    }

    private static int positiveIntProperty(String name, int fallback) {
        String raw = System.getProperty(name, "").trim();
        if (raw.isEmpty()) return fallback;
        try { int value = Integer.parseInt(raw); return value > 0 ? value : fallback; }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static long positiveLongProperty(String name, long fallback) {
        String raw = System.getProperty(name, "").trim();
        if (raw.isEmpty()) return fallback;
        try { long value = Long.parseLong(raw); return value > 0L ? value : fallback; }
        catch (NumberFormatException ignored) { return fallback; }
    }

    public synchronized void start() throws IOException {
        if (running.get()) return;
        prepareDestination(path);
        startPrepared();
    }

    private void startPrepared() {
        drainOnStop = true;
        generation++;
        running.set(true);
        thread = new Thread(this::run, "madlava-writer");
        thread.setDaemon(true);
        try { thread.start(); }
        catch (Throwable failure) { running.set(false); thread = null; throw failure; }
    }

    private static void prepareDestination(Path destination) throws IOException {
        Path normalized = destination.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        if (parent == null) throw new IOException("MadLava report path has no parent directory");
        Files.createDirectories(parent);
        Files.createDirectories(normalized.resolveSibling("segments"));
        try (BufferedWriter probe = Files.newBufferedWriter(normalized, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) { probe.flush(); }
    }

    private void run() {
        BufferedWriter out = null;
        try {
            Path active = path;
            Path segments = active.resolveSibling("segments");
            Files.createDirectories(segments);
            int index = nextSegmentIndex(segments);
            out = Files.newBufferedWriter(active, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            long currentSize = Files.exists(active) ? Files.size(active) : 0L;
            int pendingRecords = 0;
            long lastFlush = System.nanoTime();
            while (running.get() || (drainOnStop && queue.size() > 0)) {
                String line;
                try { line = queue.poll(1, TimeUnit.SECONDS); }
                catch (InterruptedException ignored) { continue; }
                long now = System.nanoTime();
                if (line == null) {
                    if (pendingRecords > 0 && now - lastFlush >= flushNanos) {
                        out.flush(); pendingRecords = 0; lastFlush = now;
                    }
                    continue;
                }
                byte[] encoded = (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
                if (currentSize > 0L && currentSize + encoded.length > maxSegmentBytes) {
                    out.flush(); pendingRecords = 0; lastFlush = now;
                    out.close();
                    Path finalized = segments.resolve(String.format("segment-%06d.jsonl", index++));
                    moveReplacing(active, finalized);
                    out = Files.newBufferedWriter(active, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    currentSize = 0L;
                }
                out.write(line);
                out.newLine();
                pendingRecords++;
                currentSize += encoded.length;
                if (pendingRecords >= flushRecords || now - lastFlush >= flushNanos) {
                    out.flush(); pendingRecords = 0; lastFlush = now;
                }
            }
            if (out != null) { out.flush(); out.close(); out = null; }
        } catch (Throwable error) {
            try { if (out != null) out.close(); } catch (IOException ignored) { }
            System.err.println("MadLava writer disabled: " + error.getClass().getSimpleName());
        } finally { running.set(false); }
    }

    @Override
    public synchronized void close() {
        if (thread == null && !running.get()) return;
        drainOnStop = true; running.set(false);
        Thread worker = thread;
        if (worker != null) {
            worker.interrupt();
            try { worker.join(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        if (worker == null || !worker.isAlive()) { finalizeManifest(); thread = null; }
    }

    public synchronized void rotate(Path nextPath) throws IOException {
        if (nextPath == null) throw new IllegalArgumentException("nextPath");
        prepareDestination(nextPath);
        if (thread == null && !running.get()) { path = nextPath; drainOnStop = true; return; }
        drainOnStop = false; running.set(false);
        Thread worker = thread;
        if (worker != null) {
            worker.interrupt();
            try { worker.join(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            if (worker.isAlive()) throw new IOException("Timed out stopping MadLava writer for output rotation");
        }
        finalizeManifest(); path = nextPath; thread = null; startPrepared();
    }

    public synchronized boolean isWorkerAlive() { return thread != null && thread.isAlive(); }
    long manifestFinalizationCount() { return manifestFinalizations.get(); }

    synchronized void finalizeManifest() {
        if (finalizedGeneration == generation) return;
        try {
            List<Path> files = reportFiles(path);
            if (files.isEmpty()) return;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            List<java.util.Map<String,Object>> fileEntries = new ArrayList<>();
            Path root = path.toAbsolutePath().normalize().getParent();
            long bytes = 0L; long records = 0L;
            byte[] buffer = new byte[64 * 1024];
            for (Path file : files) {
                long fileBytes = Files.size(file); bytes += fileBytes;
                MessageDigest fileDigest = MessageDigest.getInstance("SHA-256");
                try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
                    int read;
                    while ((read = in.read(buffer)) >= 0) {
                        if (read == 0) continue;
                        digest.update(buffer, 0, read); fileDigest.update(buffer, 0, read);
                        for (int i = 0; i < read; i++) if (buffer[i] == '\n') records++;
                    }
                }
                java.util.Map<String,Object> entry = new java.util.LinkedHashMap<>();
                entry.put("path", root.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/'));
                entry.put("bytes", fileBytes); entry.put("sha256", hex(fileDigest.digest())); fileEntries.add(entry);
            }
            String manifest = finalManifestText(path.toString(), files.size(), records, bytes, hex(digest.digest()), fileEntries);
            Files.writeString(path.resolveSibling("madlava-report-manifest.json"), manifest,
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            finalizedGeneration = generation;
            manifestFinalizations.incrementAndGet();
        } catch (Throwable ignored) { }
    }

    private static String hex(byte[] digest) { StringBuilder value = new StringBuilder(); for (byte item : digest) value.append(String.format("%02x", item)); return value.toString(); }
    static String finalManifestText(String reportPath, int segments, long records, long bytes, String sha256) { return finalManifestText(reportPath, segments, records, bytes, sha256, List.of()); }
    static String finalManifestText(String reportPath, int segments, long records, long bytes, String sha256, List<java.util.Map<String,Object>> files) {
        java.util.Map<String,Object> manifestData = new java.util.LinkedHashMap<>();
        manifestData.put("state", "FINAL"); manifestData.put("path", reportPath); manifestData.put("segments", segments);
        manifestData.put("records", records); manifestData.put("bytes", bytes); manifestData.put("sha256", sha256); manifestData.put("files", files);
        return Json.encode(manifestData) + "\n";
    }

    private static List<Path> reportFiles(Path active) throws IOException {
        List<Path> files = new ArrayList<>(); Path segments = active.resolveSibling("segments");
        if (Files.isDirectory(segments)) try (java.util.stream.Stream<Path> stream = Files.list(segments)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".jsonl")).sorted(Comparator.comparing(p -> p.getFileName().toString())).forEach(files::add);
        }
        if (Files.isRegularFile(active)) files.add(active); return files;
    }

    private static int nextSegmentIndex(Path segments) throws IOException {
        int maximum = 0; if (!Files.isDirectory(segments)) return 1;
        try (java.util.stream.Stream<Path> stream = Files.list(segments)) {
            for (Path file : (Iterable<Path>) stream::iterator) {
                String name = file.getFileName().toString(); if (!name.startsWith("segment-") || !name.endsWith(".jsonl")) continue;
                try { maximum = Math.max(maximum, Integer.parseInt(name.substring(8, name.length() - 6))); } catch (NumberFormatException ignored) { }
            }
        }
        return maximum + 1;
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }
}