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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Single-consumer JSONL writer with bounded segment rotation.
 *
 * <p>The documented {@code madlava.jsonl} path always remains the active segment.
 * Completed full segments are moved under {@code segments/}. This keeps discovery
 * manifests valid after the first size rotation.</p>
 */
public final class JsonlWriter implements AutoCloseable {
    private static final long DEFAULT_MAX_SEGMENT_BYTES = 100L * 1024L * 1024L;
    private final BoundedSnapshotQueue queue;
    private final AtomicBoolean running = new AtomicBoolean();
    private final long maxSegmentBytes;
    private volatile boolean drainOnStop = true;
    private volatile boolean closeRequested;
    private volatile Path path;
    private Thread thread;

    public JsonlWriter(BoundedSnapshotQueue queue, Path path) {
        this(queue, path, DEFAULT_MAX_SEGMENT_BYTES);
    }

    JsonlWriter(BoundedSnapshotQueue queue, Path path, long maxSegmentBytes) {
        if (queue == null || path == null || maxSegmentBytes < 1L) throw new IllegalArgumentException();
        this.queue = queue;
        this.path = path;
        this.maxSegmentBytes = maxSegmentBytes;
    }

    public synchronized void start() throws IOException {
        if (running.get()) return;
        prepareDestination(path);
        startPrepared();
    }

    private void startPrepared() {
        drainOnStop = true;
        closeRequested = false;
        running.set(true);
        thread = new Thread(this::run, "madlava-writer");
        thread.setDaemon(true);
        try {
            thread.start();
        } catch (Throwable failure) {
            running.set(false);
            thread = null;
            throw failure;
        }
    }

    /**
     * Validate the destination synchronously so agent startup / hot rotation cannot report success
     * before discovering that the report file or segment directory is unusable.
     */
    private static void prepareDestination(Path destination) throws IOException {
        Path normalized = destination.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        if (parent == null) throw new IOException("MadLava report path has no parent directory");
        Files.createDirectories(parent);
        Files.createDirectories(normalized.resolveSibling("segments"));
        try (BufferedWriter probe = Files.newBufferedWriter(normalized, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            // Opening and flushing is the preflight. The background writer owns subsequent I/O.
            probe.flush();
        }
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
            while (running.get() || (drainOnStop && queue.size() > 0)) {
                String line = queue.poll();
                if (line == null) {
                    try { Thread.sleep(10); }
                    catch (InterruptedException ignored) { /* lifecycle wake-up */ }
                    continue;
                }
                byte[] encoded = (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
                if (currentSize > 0L && currentSize + encoded.length > maxSegmentBytes) {
                    out.flush();
                    out.close();
                    Path finalized = segments.resolve(String.format("segment-%06d.jsonl", index++));
                    moveReplacing(active, finalized);
                    out = Files.newBufferedWriter(active, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    currentSize = 0L;
                }
                out.write(line);
                out.newLine();
                out.flush();
                currentSize += encoded.length;
            }
            if (out != null) { out.flush(); out.close(); out = null; }
        } catch (Throwable error) {
            try { if (out != null) out.close(); } catch (IOException ignored) { }
            System.err.println("MadLava writer disabled: " + error.getClass().getSimpleName());
        } finally {
            running.set(false);
            // If close() timed out while the sink was blocked, the daemon may finish later.
            // Finalize integrity metadata from the writer thread only after all writes have stopped.
            if (closeRequested) finalizeManifest();
        }
    }

    @Override
    public synchronized void close() {
        if (thread == null && !running.get()) return;
        drainOnStop = true;
        closeRequested = true;
        running.set(false);
        Thread worker = thread;
        if (worker != null) {
            worker.interrupt();
            try { worker.join(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        if (worker == null || !worker.isAlive()) {
            finalizeManifest();
            thread = null;
        }
        // If still alive, retain the thread reference. The reporter must retain the run lock too;
        // releasing ownership while this daemon can still write would violate single-writer safety.
    }

    public synchronized void rotate(Path nextPath) throws IOException {
        if (nextPath == null) throw new IllegalArgumentException("nextPath");
        // Prove the new destination is writable before stopping the currently healthy writer.
        prepareDestination(nextPath);
        if (thread == null && !running.get()) {
            // Reporter configuration may be bound before reporter.start(). Relocating an
            // unstarted writer must not implicitly start background I/O.
            path = nextPath;
            closeRequested = false;
            drainOnStop = true;
            return;
        }
        closeRequested = false;
        drainOnStop = false; // leave pending records in the queue for the new destination
        running.set(false);
        Thread worker = thread;
        if (worker != null) {
            worker.interrupt();
            try { worker.join(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            if (worker.isAlive()) throw new IOException("Timed out stopping MadLava writer for output rotation");
        }
        finalizeManifest();
        path = nextPath;
        thread = null;
        startPrepared();
    }

    /** True while the background writer can still touch the active report path. */
    public synchronized boolean isWorkerAlive() {
        return thread != null && thread.isAlive();
    }

    private void finalizeManifest() {
        try {
            List<Path> files = reportFiles(path);
            if (files.isEmpty()) return;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long bytes = 0L;
            long records = 0L;
            byte[] buffer = new byte[64 * 1024];
            for (Path file : files) {
                bytes += Files.size(file);
                try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
                    int read;
                    while ((read = in.read(buffer)) >= 0) {
                        if (read == 0) continue;
                        digest.update(buffer, 0, read);
                        for (int i = 0; i < read; i++) if (buffer[i] == '\n') records++;
                    }
                }
            }
            StringBuilder hex = new StringBuilder();
            for (byte value : digest.digest()) hex.append(String.format("%02x", value));
            String manifest = finalManifestText(path.toString(), files.size(), records, bytes, hex.toString());
            Files.writeString(path.resolveSibling("madlava-report-manifest.json"), manifest,
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Throwable ignored) {
            // Integrity metadata must never affect application shutdown.
        }
    }

    static String finalManifestText(String reportPath, int segments, long records, long bytes, String sha256) {
        java.util.Map<String,Object> manifestData = new java.util.LinkedHashMap<>();
        manifestData.put("state", "FINAL");
        manifestData.put("path", reportPath);
        manifestData.put("segments", segments);
        manifestData.put("records", records);
        manifestData.put("bytes", bytes);
        manifestData.put("sha256", sha256);
        return Json.encode(manifestData) + "\n";
    }

    private static List<Path> reportFiles(Path active) throws IOException {
        List<Path> files = new ArrayList<>();
        Path segments = active.resolveSibling("segments");
        if (Files.isDirectory(segments)) {
            try (java.util.stream.Stream<Path> stream = Files.list(segments)) {
                stream.filter(p -> p.getFileName().toString().endsWith(".jsonl"))
                        .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                        .forEach(files::add);
            }
        }
        if (Files.isRegularFile(active)) files.add(active);
        return files;
    }

    private static int nextSegmentIndex(Path segments) throws IOException {
        int maximum = 0;
        if (!Files.isDirectory(segments)) return 1;
        try (java.util.stream.Stream<Path> stream = Files.list(segments)) {
            for (Path file : (Iterable<Path>) stream::iterator) {
                String name = file.getFileName().toString();
                if (!name.startsWith("segment-") || !name.endsWith(".jsonl")) continue;
                try { maximum = Math.max(maximum, Integer.parseInt(name.substring(8, name.length() - 6))); }
                catch (NumberFormatException ignored) { }
            }
        }
        return maximum + 1;
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

}
