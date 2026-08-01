package com.cookieukw.SimTale.core;

import com.hypixel.hytale.logger.HytaleLogger;

/**
 * Logger do SimTale. Mantem a assinatura do SLF4J e entrega no logger do Hytale.
 *
 * <h3>Por que isto existe</h3>
 * Metade do mod logava via {@code org.slf4j.LoggerFactory}, e <b>nada disso aparecia</b>. O
 * servidor nao fornece um binding do SLF4J, entao o proprio boot avisa:
 *
 * <pre>
 *   SLF4J: Failed to load class "org.slf4j.impl.StaticLoggerBinder".
 *   SLF4J: Defaulting to no-operation (NOP) logger implementation
 * </pre>
 *
 * NOP significa descartar tudo, em silencio. Quatorze classes — RoutineAISystem, HouseManager,
 * BedRegistry, os helpers de porta, trabalho, fome e lazer — escreviam para o vazio. Isso nao e
 * so falta de informacao: mascarava diagnostico. Ao investigar por que a NPC dormia errado,
 * nenhuma linha do ciclo de sono aparecia no log, o que dava a impressao de que o codigo nem
 * estava rodando.
 *
 * <p>As classes que usavam {@code HytaleLogger} direto sempre funcionaram, e e por isso que
 * mensagens de {@code SimNPCSpawnSystem} e {@code SimTaleUseNPCInteraction} apareciam no log
 * enquanto as vizinhas nao.
 *
 * <p>A alternativa seria reescrever centenas de chamadas {@code LOGGER.info("x {}", v)} para o
 * formato do Hytale. Este adaptador troca isso por uma linha por arquivo, e mantem o estilo de
 * chamada que o codigo ja usa.
 */
public final class SimLog {

    /**
     * Chamadas {@code debug} sao descartadas por padrao.
     * <p>
     * Nao e perda: elas ja eram descartadas pelo NOP. Ligar tudo de uma vez encheria o log do
     * servidor com varreduras por tick (busca de cama, de agua, de baus). Quem precisar liga
     * pontualmente.
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

    // ---------------------------------------------------------------------
    // API no estilo SLF4J
    // ---------------------------------------------------------------------

    public void info(String format, Object... args) {
        delegate.atInfo().log(format(format, args));
    }

    public void warn(String format, Object... args) {
        delegate.atWarning().log(format(format, args));
    }

    public void error(String format, Object... args) {
        // Throwable no fim do varargs e a convencao do SLF4J: vira causa, nao argumento.
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

    // ---------------------------------------------------------------------
    // Formatacao
    // ---------------------------------------------------------------------

    /**
     * Substitui cada {@code {}} pelo argumento correspondente, como faz o SLF4J.
     * <p>
     * Argumentos sobrando sao anexados ao fim em vez de sumirem: uma mensagem com um
     * placeholder a menos ainda mostra o dado, o que e melhor do que perde-lo justo quando
     * alguem esta investigando um problema.
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

        // O ultimo argumento pode ser a causa de um error(); nesse caso ele ja foi tratado.
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
            // Um toString() quebrado nao pode derrubar quem so queria logar.
            return o.getClass().getSimpleName() + "@<toString falhou>";
        }
    }

    private static Throwable extractCause(Object[] args) {
        if (args == null || args.length == 0) return null;
        Object last = args[args.length - 1];
        return last instanceof Throwable ? (Throwable) last : null;
    }
}
