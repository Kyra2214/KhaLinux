package com.khalinux.runtime;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public final class NativePtyIntegrationTest {
    public static void main(String[] args) throws Exception {
        String script = "stty size; IFS= read -r line; printf 'done:%s\\n' \"$line\"; " +
                "printf 'utf8:á🌱\\n'; exit 7";
        int[] child = NativePty.spawn(
                new String[]{"/bin/sh", "-c", script},
                new String[]{"PATH=/usr/bin:/bin"},
                24,
                80
        );
        int master = child[0];
        int pid = child[1];
        try {
            require(NativePty.resize(master, 42, 100), "resize não aplicado");
            byte[] input = "pty-input\n".getBytes(StandardCharsets.UTF_8);
            require(NativePty.write(master, input, 0, input.length) == input.length, "entrada incompleta");

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = NativePty.read(master, buffer, 0, buffer.length)) >= 0) {
                output.write(buffer, 0, count);
            }
            String text = output.toString(StandardCharsets.UTF_8);
            require(text.contains("42 100"), "o processo não observou o resize: " + text);
            require(text.contains("done:pty-input"), "o filho não recebeu a entrada: " + text);
            require(text.contains("utf8:á🌱"), "argv UTF-8 não foi preservado: " + text);
            require(NativePty.waitFor(pid) == 7, "código de saída inesperado");
        } finally {
            NativePty.close(master);
        }

        int[] signalled = NativePty.spawn(
                new String[]{"/bin/sleep", "30"},
                new String[]{"PATH=/usr/bin:/bin"},
                24,
                80
        );
        try {
            require(NativePty.signal(signalled[1], true), "SIGKILL não enviado");
            require(NativePty.waitFor(signalled[1]) == 137, "processo não encerrou pelo sinal esperado");
        } finally {
            NativePty.close(signalled[0]);
        }

        int[] terminated = NativePty.spawn(
                new String[]{"/bin/sleep", "30"},
                new String[]{"PATH=/usr/bin:/bin"},
                24,
                80
        );
        try {
            require(NativePty.signal(terminated[1], false), "SIGTERM não enviado");
            int termExit = NativePty.waitFor(terminated[1]);
            require(termExit == 143, "processo não encerrou por SIGTERM; status=" + termExit);
        } finally {
            NativePty.close(terminated[0]);
        }

        System.out.println("pty_native=ok spawn=true input=true resize=true utf8=true signals=true");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
