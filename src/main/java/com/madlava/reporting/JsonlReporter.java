package com.madlava.reporting;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.management.ManagementFactory;

/** Single-threaded JSONL writer; application threads never perform file I/O. */
public final class JsonlReporter implements Closeable {
    private final AgentRuntime runtime;
    private final Path reportPath;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final BoundedSnapshotQueue queue;
    private final JsonlWriter writer;
    private final FileChannel ownershipChannel;
    private final FileLock ownershipLock;

    public JsonlReporter(AgentRuntime runtime, Path outputDirectory) throws IOException {
        this.runtime = runtime;
        String pid = ManagementFactory.getRuntimeMXBean().getName().split("@", 2)[0];
        Path runDirectory = outputDirectory.resolve("run-" + pid + "-" + Long.toUnsignedString(System.nanoTime()));
        Files.createDirectories(runDirectory);
        this.reportPath = runDirectory.resolve("madlava.jsonl");
        this.queue = new BoundedSnapshotQueue(256);
        this.writer = new JsonlWriter(queue, reportPath);
        Path manifest = outputDirectory.resolve("madlava-run-" + pid + ".json");
        String manifestText = "{\"pid\":\"" + escape(pid) + "\",\"report\":\""
                + escape(this.reportPath.toAbsolutePath().normalize().toString()) + "\"}\n";
        Files.writeString(manifest, manifestText, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Path lockPath = runDirectory.resolve("madlava.run.lock");
        this.ownershipChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            this.ownershipLock = ownershipChannel.tryLock();
        } catch (OverlappingFileLockException failure) {
            ownershipChannel.close();
            throw new IOException("MadLava output directory is already owned by another JVM", failure);
        }
        if (ownershipLock == null) {
            ownershipChannel.close();
            throw new IOException("MadLava output directory is already owned by another JVM");
        }
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "madlava-snapshot-writer");
            thread.setDaemon(true);
            return thread;
        };
        this.scheduler = Executors.newSingleThreadScheduledExecutor(factory);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public Path reportPath() {
        return reportPath;
    }

    public void start(int intervalSeconds, boolean shutdownOnly) {
        try {
            writer.start();
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to start MadLava report writer", failure);
        }
        writeSafely("startup");
        if (!shutdownOnly) {
            scheduler.scheduleWithFixedDelay(
                    () -> writeSafely("periodic"),
                    intervalSeconds,
                    intervalSeconds,
                    TimeUnit.SECONDS);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        scheduler.shutdownNow();
        writeSafely("shutdown");
        writer.close();
        try {
            ownershipLock.release();
            ownershipChannel.close();
        } catch (IOException ignored) {
            // Ownership cleanup must not affect application shutdown.
        }
    }

    private void writeSafely(String reason) {
        try {
            write(reason);
        } catch (Throwable ignored) {
            // Reporting failure must not affect the observed process.
        }
    }

    private void write(String reason) throws IOException {
        queue.submit(Json.encode(runtime.snapshot(reason)));
    }
}
