package com.cookieukw.SimTale.core;

import com.hypixel.hytale.logger.HytaleLogger;

/**
 * SimTale logger. Preserves the SLF4J signature and delivers into Hytale's logger.
 *
 * <h3>Why this exists</h3>
 * Half the mod logged via {@code org.slf4j.LoggerFactory}, and <b>none of it appeared</b>. The
 * server does not provide an SLF4J binding, so the boot process itself warns:
 *
 * <pre>
 *   SLF4J: Failed to load class "org.slf4j.impl.StaticLoggerBinder".
 *   SLF4J: Defaulting to no-operation (NOP) logger implementation
 * </pre>
 *
 * NOP means discarding everything silently. Fourteen classes — RoutineAISystem, HouseManager,
 * BedRegistry, helpers for doors, work, hunger and leisure — wrote to the void. This was not just
 * lack of information: it masked diagnostics. When investigating why an NPC slept incorrectly,
 * no lines of the sleep cycle appeared in the log, giving the impression that the code wasn't even
 * running.
 *
 * <p>Classes that used {@code HytaleLogger} directly always worked, which is why messages from
 * {@code SimNPCSpawnSystem} and {@code SimTaleUseNPCInteraction} appeared in the log while
 * neighboring classes did not.
 *
 * <p>The alternative would be rewriting hundreds of {@code LOGGER.info("x {}", v)} calls into
 * Hytale format. This adapter replaces that with one line per file, preserving the calling style
 * the codebase already uses.
 */
public final class SimLog {

    /**
     * {@code debug} calls are discarded by default.
     * <p>
     * No loss: they were already discarded by NOP. Enabling everything at once would flood the
     * server log with per-tick scans (searching for beds, water, chests). Enable selectively when
     * needed.
     */
    public static volatile boolean debugEnabled = false;

    private final HytaleLogger delegate;

    private SimLog(HytaleLogger delegate) {
        this.delegate = delegate;
    }

    public static SimLog forClass(Class<?> owner) {
        return new SimLog(HytaleLogger.get(owner.getSimpleName()));
    }

    public static SimLog named(String name) {
        return new SimLog(HytaleLogger.get(name));
    }

    /* ---------------------------------------------------------------------
    SLF4J-style API
    ---------------------------------------------------------------------
    */

    public void info(String format, Object... args) {
        delegate.atInfo().log(format(format, args));
    }

    public void warn(String format, Object... args) {
        delegate.atWarning().log(format(format, args));
    }

    public void error(String format, Object... args) {
        // Throwable at the end of varargs is the SLF4J convention: it becomes the cause, not an argument.
        Throwable cause = extractCause(args);
        if (cause != null) {
            delegate.atSevere().withCause(cause).log(format(format, args));
        } else {
            delegate.atSevere().log(format(format, args));
        }
    }

    public void debug(String format, Object... args) {
        if (debugEnabled) {
            delegate.atInfo().log("[debug] " + format(format, args));
        }
    }

    public boolean isInfoEnabled() {
        return true;
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public boolean isWarnEnabled() {
        return true;
    }

    /* ---------------------------------------------------------------------
    Formatting
    ---------------------------------------------------------------------
    */

    /**
     * Replaces each {@code {}} with the corresponding argument, just like SLF4J does.
     * <p>
     * Leftover arguments are appended at the end instead of disappearing: a message with one
     * placeholder too few still shows the data, which is better than losing it right when someone
     * is investigating an issue.
     */
    private static String format(String format, Object... args) {
        if (format == null) return "null";
        if (args == null || args.length == 0) return format;

        StringBuilder out = new StringBuilder(format.length() + 32);
        int arg = 0;
        int i = 0;
        while (i < format.length()) {
            if (arg < args.length && i + 1 < format.length()
                    && format.charAt(i) == '{' && format.charAt(i + 1) == '}') {
                out.append(str(args[arg++]));
                i += 2;
            } else {
                out.append(format.charAt(i));
                i++;
            }
        }

        // The last argument may be the cause of an error(); in that case it was already handled.
        int remaining = args.length;
        if (remaining > 0 && args[remaining - 1] instanceof Throwable) {
            remaining--;
        }
        for (int extra = arg; extra < remaining; extra++) {
            out.append(extra == arg ? " [" : ", ").append(str(args[extra]));
            if (extra == remaining - 1) out.append(']');
        }
        return out.toString();
    }

    private static String str(Object o) {
        if (o == null) return "null";
        try {
            return String.valueOf(o);
        } catch (Exception e) {
            // A broken toString() must not crash whatever just wanted to log.
            return o.getClass().getSimpleName() + "@<toString failed>";
        }
    }

    private static Throwable extractCause(Object[] args) {
        if (args == null || args.length == 0) return null;
        Object last = args[args.length - 1];
        return last instanceof Throwable ? (Throwable) last : null;
    }
}
