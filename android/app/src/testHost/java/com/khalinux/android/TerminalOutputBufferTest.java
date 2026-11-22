package com.khalinux.android;

public final class TerminalOutputBufferTest {
    public static void main(String[] args) {
        preservesShortTranscript();
        truncatesOldOutputAndKeepsRecentTail();
        handlesSingleOversizedChunk();
        defaultConstructorEnforcesCap();
        clearResetsTruncationState();
        rejectsImpossibleLimit();
        System.out.println("terminal_output_buffer=ok cases=6");
    }

    private static void preservesShortTranscript() {
        TerminalOutputBuffer buffer = new TerminalOutputBuffer(64);
        buffer.append("$ echo ok\nok\n");
        require("$ echo ok\nok\n".equals(buffer.toString()), "short transcript changed");
        require(buffer.length() <= 64, "short transcript exceeds its limit");
    }

    private static void truncatesOldOutputAndKeepsRecentTail() {
        TerminalOutputBuffer buffer = new TerminalOutputBuffer(96);
        buffer.append("old-output-".repeat(20));
        buffer.append("RECENT");
        String result = buffer.toString();
        require(result.startsWith("[saída anterior truncada"), "missing truncation marker");
        require(result.endsWith("RECENT"), "recent output was not retained");
        require(result.length() <= 96, "transcript exceeds configured limit");
        require(result.indexOf("[saída anterior truncada") == result.lastIndexOf("[saída anterior truncada"),
                "truncation marker duplicated");
    }

    private static void handlesSingleOversizedChunk() {
        TerminalOutputBuffer buffer = new TerminalOutputBuffer(80);
        String oversized = "x".repeat(200) + "TAIL";
        buffer.append(oversized);
        String result = buffer.toString();
        require(result.startsWith("[saída anterior truncada"), "oversized chunk was not marked");
        require(result.endsWith("TAIL"), "tail of oversized chunk was lost");
        require(result.length() <= 80, "oversized chunk exceeds configured limit");
    }

    private static void defaultConstructorEnforcesCap() {
        TerminalOutputBuffer buffer = new TerminalOutputBuffer();
        buffer.append("x".repeat(TerminalOutputBuffer.DEFAULT_MAX_CHARS + 100) + "DEFAULT-TAIL");
        String result = buffer.toString();
        require(result.startsWith("[saída anterior truncada"), "default limit did not mark truncation");
        require(result.endsWith("DEFAULT-TAIL"), "default limit lost recent output");
        require(result.length() <= TerminalOutputBuffer.DEFAULT_MAX_CHARS, "default limit was exceeded");
    }

    private static void clearResetsTruncationState() {
        TerminalOutputBuffer buffer = new TerminalOutputBuffer(64);
        buffer.append("x".repeat(100));
        buffer.clear();
        buffer.append("fresh");
        require("fresh".equals(buffer.toString()), "clear did not reset buffer state");
    }

    private static void rejectsImpossibleLimit() {
        boolean rejected = false;
        try {
            new TerminalOutputBuffer(8);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, "impossible limit was accepted");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
