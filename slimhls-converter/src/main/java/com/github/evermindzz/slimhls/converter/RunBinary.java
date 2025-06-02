package com.github.evermindzz.slimhls.converter;

import android.os.Build;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class RunBinary {

    private static final long INACTIVITY_THRESHOLD_SECONDS = 30; // Terminate if no output for 30 seconds
    private static final int LOG_EXECUTOR_TIMEOUT_SECONDS = 1;   // Timeout for log executor shutdown

    private final ArrayList<String> command;
    private final String logPrefixMsg;

    public RunBinary(String binaryPath, String logPrefixMsg) {
        command = new ArrayList<>();
        if (binaryPath == null)
            throw new NullPointerException();
        command.add(binaryPath);
        this.logPrefixMsg = logPrefixMsg;
    }

    /**
     * See {@link #execute(List)}.
     */
    public int execute(String... args) throws IOException {
        return execute(Arrays.asList(args));
    }

    /**
     * Executes the binary.
     * <p>
     * Monitors the binary output to detect inactivity and terminates the process if no progress
     * is made within {@link #INACTIVITY_THRESHOLD_SECONDS}.
     *
     * @param  args         the args for the binary
     * @throws IOException  If binary execution or file operations fail.
     * @return              the exit code of the binary
     */
    public int execute(List<String> args) throws IOException {
        if (args == null)
            throw new NullPointerException();
        this.command.addAll(args);

        Process process = null;
        int exitCode = -1;

        ExecutorService logExecutor = Executors.newSingleThreadExecutor();
        try {
            AtomicReference<Instant> lastOutputTime = new AtomicReference<>(Instant.now());
            try {
                // Configure binary process commandline
                ProcessBuilder builder = new ProcessBuilder(this.command);
                builder.redirectErrorStream(true); // Merge stdout and stderr for logging
                process = builder.start();

                // Asynchronously log output and monitor inactivity
                Process finalProcess = process;
                logExecutor.submit(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(finalProcess.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            System.out.println("[" + logPrefixMsg + "] " + line); // Log output
                            lastOutputTime.set(Instant.now());     // Update last output time
                        }
                    } catch (IOException e) {
                        System.err.println("Error reading output: " + e.getMessage());
                    }
                });

                // Watchdog to check for inactivity
                Instant start = Instant.now();
                while (LegacyProcessUtils.isAlive(process)) {
                    Duration inactivityDuration = Duration.between(lastOutputTime.get(), Instant.now());
                    if (inactivityDuration.getSeconds() > INACTIVITY_THRESHOLD_SECONDS) {
                        System.err.println(logPrefixMsg + " stalled for " + INACTIVITY_THRESHOLD_SECONDS + " seconds, terminating...");
                        LegacyProcessUtils.destroy(process);
                        throw new BinaryRuntimeException(logPrefixMsg + " stalled due to inactivity", null);
                    }
                    Thread.sleep(1000); // Check every second
                }

                // Measure execution time
                Duration duration = Duration.between(start, Instant.now());
                System.out.println(logPrefixMsg + " execution took " + duration.toMillis() + " ms");

                // Check exit code after process completes
                exitCode = process.exitValue();
                if (exitCode != 0) {
                    System.err.println(logPrefixMsg + " failed with exit code: " + exitCode);
                }
            } catch (InterruptedException e) {
                System.err.println(logPrefixMsg + " thread could not sleep " + e.getMessage());
            } finally {
                // Clean up process
                if (process != null) {
                    LegacyProcessUtils.destroy(process); // Ensure process is terminated
                }
                logExecutor.shutdown();
                try {
                    logExecutor.awaitTermination(LOG_EXECUTOR_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        } finally {
            logExecutor.shutdown(); // Satisfy Java 8
        }

        return exitCode;
    }

    private static class LegacyProcessUtils {

        private LegacyProcessUtils() {
            // No instance
        }

        /**
         * Checks if the process is alive in a backwards-compatible way.
         * On SDK < 26, there is no direct way to check if the process is alive,
         * so we attempt to use exitValue() to see if it throws an exception.
         */
        public static boolean isAlive(Process process) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return process.isAlive();
            } else {
                try {
                    process.exitValue();
                    return false; // Process has exited
                } catch (IllegalThreadStateException e) {
                    return true; // Process still running
                }
            }
        }

        /**
         * Destroys the process forcibly if supported, otherwise calls destroy().
         */
        public static void destroy(Process process) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                process.destroyForcibly();
            } else {
                process.destroy();
            }
        }
    }

    /**
     * Custom exception for failures during runtime of the binary.
     */
    private static class BinaryRuntimeException extends RuntimeException {
        BinaryRuntimeException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
