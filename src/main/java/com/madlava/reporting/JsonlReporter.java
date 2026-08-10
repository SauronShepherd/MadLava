package com.madlava.reporting;

import com.madlava.config.ConfigurationChangeEvent;
import com.madlava.config.RuntimeConfigurationManager;
import com.madlava.tracing.TraceDispatcher;

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
    private final String pid;
    private volatile Path outputRoot;
    private volatile Path reportPath;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean closed = new AtomicBoolean();
    /** Guarded by this. */
    private boolean started;
    private final BoundedSnapshotQueue queue;
    private final JsonlWriter writer;
    private FileChannel ownershipChannel;
    private FileLock ownershipLock;
    private RuntimeConfigurationManager.Listener configurationListener;
    private RuntimeConfigurationManager.RejectionListener rejectionListener;
    private RuntimeConfigurationManager.TransitionValidator configurationValidator;
    private RuntimeConfigurationManager configurationManager;
    private final TraceDispatcher traceDispatcher;

    public JsonlReporter(AgentRuntime runtime, Path outputDirectory) throws IOException {
        this.runtime = runtime;
        String pid = ManagementFactory.getRuntimeMXBean().getName().split("@", 2)[0];
        Path normalizedOutput = outputDirectory.toAbsolutePath().normalize();
        Files.createDirectories(normalizedOutput);
        Path runDirectory = Files.createTempDirectory(normalizedOutput, "run-" + pid + "-");
        Path initialReportPath = runDirectory.resolve("madlava.jsonl");
        BoundedSnapshotQueue initialQueue = new BoundedSnapshotQueue(256);
        JsonlWriter initialWriter = new JsonlWriter(initialQueue, initialReportPath);
        FileChannel channel = null;
        FileLock lock = null;
        TraceDispatcher dispatcher = null;
        ScheduledExecutorService initialScheduler = null;
        try {
            Path lockPath = runDirectory.resolve("madlava.run.lock");
            channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException failure) {
                throw new IOException("MadLava output directory is already owned by another JVM", failure);
            }
            if (lock == null) throw new IOException("MadLava output directory is already owned by another JVM");

            dispatcher = new TraceDispatcher(1024, event -> initialQueue.submit(Json.encode(event)));
            ThreadFactory factory = runnable -> {
                Thread thread = new Thread(runnable, "madlava-snapshot-writer");
                thread.setDaemon(true);
                return thread;
            };
            initialScheduler = Executors.newSingleThreadScheduledExecutor(factory);
            // Discovery metadata is published by start(), after the writer destination has
            // synchronously passed its openability preflight and the worker has been started.
        } catch (Throwable failure) {
            if (initialScheduler != null) initialScheduler.shutdownNow();
            if (dispatcher != null) dispatcher.close();
            try { if (lock != null) lock.release(); } catch (Throwable ignored) { }
            try { if (channel != null) channel.close(); } catch (Throwable ignored) { }
            cleanupRunDirectory(runDirectory);
            if (failure instanceof IOException) throw (IOException) failure;
            if (failure instanceof RuntimeException) throw (RuntimeException) failure;
            if (failure instanceof Error) throw (Error) failure;
            throw new IOException("Unable to initialize MadLava reporter", failure);
        }

        this.pid = pid;
        this.outputRoot = normalizedOutput;
        this.reportPath = initialReportPath;
        this.queue = initialQueue;
        this.writer = initialWriter;
        this.traceDispatcher = dispatcher;
        this.scheduler = initialScheduler;
        this.ownershipChannel = channel;
        this.ownershipLock = lock;
    }

    private static void writeDiscoveryManifest(Path outputDirectory, String pid, Path report) throws IOException {
        Files.createDirectories(outputDirectory);
        Path manifest = outputDirectory.resolve("madlava-run-" + pid + ".json");
        String manifestText = discoveryManifestText(pid, report.toAbsolutePath().normalize().toString());
        Path temporary = manifest.resolveSibling(manifest.getFileName() + ".tmp-" + Long.toUnsignedString(System.nanoTime()));
        Files.writeString(temporary, manifestText, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        try {
            Files.move(temporary, manifest, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, manifest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static String discoveryManifestText(String pid, String reportPath) {
        return Json.encode(java.util.Map.of(
                "pid", pid,
                "report", reportPath)) + "\n";
    }

    private static void deleteDiscoveryManifest(Path outputDirectory, String pid) {
        try { Files.deleteIfExists(outputDirectory.resolve("madlava-run-" + pid + ".json")); }
        catch (IOException ignored) { /* failed startup must remain fail-open */ }
    }

    private static void cleanupRunDirectory(Path runDirectory) {
        if (runDirectory == null || !Files.exists(runDirectory)) return;
        try (java.util.stream.Stream<Path> paths = Files.walk(runDirectory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { /* cleanup is best-effort */ }
    }


    public Path reportPath() {
        return reportPath;
    }

    public boolean submitTraceEvent(java.util.Map<String, Object> event) { return traceDispatcher.submit(event); }
    public long traceEventsProduced() { return traceDispatcher.produced(); }
    public long traceEventsDropped() { return traceDispatcher.dropped(); }
    public long traceSinkFailures() { return traceDispatcher.sinkFailures(); }

    public synchronized void bindConfiguration(RuntimeConfigurationManager manager) {
        if (manager == null || configurationListener != null) return;
        configurationManager = manager;
        configurationValidator = (previous, proposed) -> {
            Object oldOutput = previous.values().get("output.directory");
            Object newOutput = proposed.values().get("output.directory");
            if (java.util.Objects.equals(oldOutput, newOutput) || newOutput == null) return null;
            try {
                Path root = java.nio.file.Paths.get(String.valueOf(newOutput)).toAbsolutePath().normalize();
                Files.createDirectories(root);
                if (!Files.isDirectory(root) || !Files.isWritable(root)) return "OUTPUT_DIRECTORY_UNAVAILABLE";
                return null;
            } catch (Throwable failure) {
                return "INVALID_OUTPUT_DIRECTORY";
            }
        };
        manager.addTransitionValidator(configurationValidator);
        configurationListener = (previous, current) -> {
            Object oldOutput = previous.values().get("output.directory");
            Object newOutput = current.values().get("output.directory");
            if (!java.util.Objects.equals(oldOutput, newOutput) && newOutput != null) {
                try {
                    rotateOutput(java.nio.file.Paths.get(String.valueOf(newOutput)));
                } catch (IOException failure) {
                    // Do not silently claim the configuration side effect succeeded. The
                    // configuration manager records listener failures in its update result.
                    throw new IllegalStateException("MadLava output rotation failed", failure);
                }
            }
            try {
                queue.submit(Json.encode(ConfigurationChangeEvent.accepted(previous, current)));
            } catch (Throwable ignored) {
                // Reporting the change event is best-effort and must not undo a successful
                // writer rotation. Queue/drop diagnostics account for pressure separately.
            }
        };
        rejectionListener = (current, reason) -> {
            try { queue.submit(Json.encode(ConfigurationChangeEvent.rejected(current, reason))); }
            catch (Throwable ignored) { }
        };
        manager.addListener(configurationListener);
        manager.addRejectionListener(rejectionListener);
    }

    private synchronized void rotateOutput(Path output) throws IOException {
        if (closed.get()) throw new IOException("MadLava reporter is closed");
        Path root = output.toAbsolutePath().normalize();
        Files.createDirectories(root);
        Path run = Files.createTempDirectory(root, "run-" + pid + "-");
        Path next = run.resolve("madlava.jsonl");

        FileChannel nextChannel = null;
        FileLock nextLock = null;
        boolean activated = false;
        try {
            nextChannel = FileChannel.open(run.resolve("madlava.run.lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            try {
                nextLock = nextChannel.tryLock();
            } catch (OverlappingFileLockException failure) {
                throw new IOException("MadLava output directory is already owned by another JVM", failure);
            }
            if (nextLock == null) throw new IOException("MadLava output directory is already owned by another JVM");

            // Do not release ownership of the current run until the writer has successfully moved.
            writer.rotate(next);
            activated = true;

            FileLock previousLock = ownershipLock;
            FileChannel previousChannel = ownershipChannel;
            Path previousRun = reportPath.getParent();
            Path previousOutputRoot = outputRoot;
            boolean previousRunWasStarted = started;
            ownershipLock = nextLock;
            ownershipChannel = nextChannel;
            nextLock = null;
            nextChannel = null;
            reportPath = next;
            outputRoot = root;
            releaseOwnership(previousLock, previousChannel);
            if (!previousRunWasStarted) cleanupRunDirectory(previousRun);

            // Discovery metadata follows only a destination whose writer lifecycle is active.
            // Publish the replacement first, then remove the old-root pointer so discovery never
            // observes a gap on a successful rotation and never leaves a stale active-run pointer.
            if (started) {
                writeDiscoveryManifest(root, pid, next);
                if (!previousOutputRoot.equals(root)) deleteDiscoveryManifest(previousOutputRoot, pid);
            }
        } finally {
            releaseOwnership(nextLock, nextChannel);
            if (!activated) cleanupRunDirectory(run);
        }
    }

    private static void releaseOwnership(FileLock lock, FileChannel channel) {
        try { if (lock != null && lock.isValid()) lock.release(); }
        catch (IOException ignored) { }
        try { if (channel != null && channel.isOpen()) channel.close(); }
        catch (IOException ignored) { }
    }

    public synchronized void start(int intervalSeconds, boolean shutdownOnly) {
        if (closed.get()) throw new IllegalStateException("MadLava reporter is closed");
        if (started) return;
        if (!shutdownOnly && intervalSeconds < 1)
            throw new IllegalArgumentException("intervalSeconds must be positive");
        try {
            writer.start();
            writeSafely("startup");
            if (!shutdownOnly) {
                scheduler.scheduleWithFixedDelay(
                        () -> writeSafely("periodic"),
                        intervalSeconds,
                        intervalSeconds,
                        TimeUnit.SECONDS);
            }
            // Publish only after every synchronous start step has succeeded.
            writeDiscoveryManifest(outputRoot, pid, reportPath);
            started = true;
        } catch (IOException | RuntimeException failure) {
            // A failed start is terminal for this reporter: stop all background components,
            // release run ownership, remove any stale same-PID discovery pointer, and clean the
            // unique run directory because it was never successfully advertised as active.
            Path failedRun = reportPath.getParent();
            close();
            deleteDiscoveryManifest(outputRoot, pid);
            if (!writer.isWorkerAlive()) cleanupRunDirectory(failedRun);
            throw new IllegalStateException("Unable to start MadLava report writer", failure);
        }
    }

    @Override
    public synchronized void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        scheduler.shutdownNow();
        try { scheduler.awaitTermination(3, TimeUnit.SECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        traceDispatcher.close();
        if (configurationManager != null && configurationListener != null) {
            configurationManager.removeListener(configurationListener);
            configurationManager.removeRejectionListener(rejectionListener);
            configurationManager.removeTransitionValidator(configurationValidator);
        }
        writeSafely("shutdown");
        writer.close();
        // A timed-out daemon writer may still be inside filesystem I/O. Retaining the lock
        // until process exit is safer than advertising the run as available to another writer.
        if (!writer.isWorkerAlive()) {
            releaseOwnership(ownershipLock, ownershipChannel);
            ownershipLock = null;
            ownershipChannel = null;
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
        String encoded = Json.encode(runtime.snapshot(reason));
        // A periodic task may already be running when close() starts. Never let it append after
        // the explicit shutdown snapshot and make the logical report end on a stale periodic row.
        if (closed.get() && !"shutdown".equals(reason)) return;
        queue.submit(encoded);
    }
}
