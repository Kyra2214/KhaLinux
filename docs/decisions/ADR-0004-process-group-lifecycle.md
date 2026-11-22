# ADR-0004: Process-group lifecycle for non-interactive commands

- Status: Accepted
- Date: 2026-11-22
- Context: The runtime must cancel a PRoot command together with its guest descendants. Calling `Process.destroy()` on the ProcessBuilder parent alone does not guarantee that child processes release inherited stdout/stderr pipes or stop promptly.

## Decision

- Keep non-interactive command I/O on ordinary stdout/stderr pipes.
- On Android, when `/system/bin/setsid` is executable, launch the command through `setsid` so the command becomes the leader of a dedicated session/process group.
- Wrap the ProcessBuilder process in `ProcessGroupManagedProcess`.
- Send normal and forced termination through the existing JNI signal bridge, which targets the negative process ID (the process group) and falls back to the direct PID when the group no longer exists.
- Keep the direct ProcessBuilder fallback on hosts without Android's `/system/bin/setsid`; interactive shell sessions continue to use the native PTY launcher.

## Consequences

- Cancelling a command can terminate PRoot and guest descendants as one process group without changing the pipe-based output contract.
- The JNI library is loaded lazily when group termination is requested. If unavailable, the wrapper falls back to terminating the direct ProcessBuilder process.
- A host-side regression test exercises a setsid process with a child process and verifies group cancellation.
- Android device confirmation remains part of the final physical validation stage; this ADR does not claim that the final handset test has occurred.

## Validation

- Runtime unit test: process-group cancellation of a shell and its child process.
- Android NDK build: required to verify the JNI bridge is packaged for the target ABI.
- Final physical Android validation: pending by project policy.
