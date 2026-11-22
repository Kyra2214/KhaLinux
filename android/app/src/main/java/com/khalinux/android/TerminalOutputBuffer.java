package com.khalinux.android;

/** Keeps only a bounded, recent terminal transcript in memory. */
public final class TerminalOutputBuffer {
    public static final int DEFAULT_MAX_CHARS = 64 * 1024;
    private static final String TRUNCATION_MARKER = "[saída anterior truncada para limitar memória]\n";

    private final int maxChars;
    private final int maxContentChars;
    private final StringBuilder content = new StringBuilder();
    private boolean truncated;

    public TerminalOutputBuffer() {
        this(DEFAULT_MAX_CHARS);
    }

    public TerminalOutputBuffer(int maxChars) {
        if (maxChars <= TRUNCATION_MARKER.length()) {
            throw new IllegalArgumentException("maxChars deve ser maior que o marcador de truncamento");
        }
        this.maxChars = maxChars;
        this.maxContentChars = maxChars - TRUNCATION_MARKER.length();
    }

    public synchronized void append(CharSequence text) {
        if (text == null || text.length() == 0) return;

        if (!truncated && content.length() + text.length() <= maxChars) {
            content.append(text);
            return;
        }

        truncated = true;
        if (text.length() >= maxContentChars) {
            content.setLength(0);
            content.append(text, text.length() - maxContentChars, text.length());
            return;
        }

        content.append(text);
        int excess = content.length() - maxContentChars;
        if (excess > 0) content.delete(0, excess);
    }

    public synchronized void clear() {
        content.setLength(0);
        truncated = false;
    }

    public synchronized int length() {
        return content.length() + (truncated ? TRUNCATION_MARKER.length() : 0);
    }

    @Override
    public synchronized String toString() {
        return truncated ? TRUNCATION_MARKER + content : content.toString();
    }
}
