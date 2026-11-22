#define _GNU_SOURCE
#include <jni.h>
#include <pty.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <signal.h>
#include <fcntl.h>
#include <errno.h>
#include <limits.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#define MAX_IO_BYTES (1024 * 1024)

static void throw_named(JNIEnv *env, const char *class_name, const char *message) {
    if ((*env)->ExceptionCheck(env)) return;
    jclass exception_class = (*env)->FindClass(env, class_name);
    if (exception_class != NULL) {
        (*env)->ThrowNew(env, exception_class, message);
        (*env)->DeleteLocalRef(env, exception_class);
    }
}

static void throw_io_error(JNIEnv *env, const char *operation, int error_number) {
    char message[256];
    (void)snprintf(message, sizeof(message), "%s: %s", operation, strerror(error_number));
    throw_named(env, "java/io/IOException", message);
}

static char *java_string_to_utf8(JNIEnv *env, jstring value) {
    if (value == NULL) {
        throw_named(env, "java/lang/IllegalArgumentException", "string array contains null");
        return NULL;
    }

    jsize length = (*env)->GetStringLength(env, value);
    const jchar *characters = (*env)->GetStringChars(env, value, NULL);
    if (characters == NULL) return NULL;

    size_t capacity = (size_t)length * 3U + 1U;
    char *encoded = (char *)malloc(capacity);
    if (encoded == NULL) {
        (*env)->ReleaseStringChars(env, value, characters);
        throw_named(env, "java/lang/OutOfMemoryError", "unable to encode PTY argument");
        return NULL;
    }

    size_t output = 0;
    for (jsize index = 0; index < length; index++) {
        uint32_t code_point = (uint32_t)characters[index];
        if (code_point == 0U) {
            free(encoded);
            (*env)->ReleaseStringChars(env, value, characters);
            throw_named(env, "java/lang/IllegalArgumentException", "PTY arguments cannot contain NUL");
            return NULL;
        }
        if (code_point >= 0xD800U && code_point <= 0xDBFFU) {
            if (index + 1 >= length) {
                free(encoded);
                (*env)->ReleaseStringChars(env, value, characters);
                throw_named(env, "java/lang/IllegalArgumentException", "unpaired UTF-16 surrogate in PTY argument");
                return NULL;
            }
            uint32_t low = (uint32_t)characters[++index];
            if (low < 0xDC00U || low > 0xDFFFU) {
                free(encoded);
                (*env)->ReleaseStringChars(env, value, characters);
                throw_named(env, "java/lang/IllegalArgumentException", "unpaired UTF-16 surrogate in PTY argument");
                return NULL;
            }
            code_point = 0x10000U + (((code_point - 0xD800U) << 10U) | (low - 0xDC00U));
        } else if (code_point >= 0xDC00U && code_point <= 0xDFFFU) {
            free(encoded);
            (*env)->ReleaseStringChars(env, value, characters);
            throw_named(env, "java/lang/IllegalArgumentException", "unpaired UTF-16 surrogate in PTY argument");
            return NULL;
        }

        if (code_point <= 0x7FU) {
            encoded[output++] = (char)code_point;
        } else if (code_point <= 0x7FFU) {
            encoded[output++] = (char)(0xC0U | (code_point >> 6U));
            encoded[output++] = (char)(0x80U | (code_point & 0x3FU));
        } else if (code_point <= 0xFFFFU) {
            encoded[output++] = (char)(0xE0U | (code_point >> 12U));
            encoded[output++] = (char)(0x80U | ((code_point >> 6U) & 0x3FU));
            encoded[output++] = (char)(0x80U | (code_point & 0x3FU));
        } else {
            encoded[output++] = (char)(0xF0U | (code_point >> 18U));
            encoded[output++] = (char)(0x80U | ((code_point >> 12U) & 0x3FU));
            encoded[output++] = (char)(0x80U | ((code_point >> 6U) & 0x3FU));
            encoded[output++] = (char)(0x80U | (code_point & 0x3FU));
        }
    }
    encoded[output] = '\0';
    (*env)->ReleaseStringChars(env, value, characters);
    return encoded;
}

static char **copy_string_array(JNIEnv *env, jobjectArray values, jsize *count_out) {
    if (values == NULL) {
        throw_named(env, "java/lang/IllegalArgumentException", "string array must not be null");
        return NULL;
    }
    jsize count = (*env)->GetArrayLength(env, values);
    char **result = (char **)calloc((size_t)count + 1U, sizeof(char *));
    if (result == NULL) {
        throw_named(env, "java/lang/OutOfMemoryError", "unable to allocate PTY string array");
        return NULL;
    }
    for (jsize index = 0; index < count; index++) {
        jstring item = (jstring)(*env)->GetObjectArrayElement(env, values, index);
        result[index] = java_string_to_utf8(env, item);
        if (item != NULL) (*env)->DeleteLocalRef(env, item);
        if (result[index] == NULL) {
            for (jsize allocated = 0; allocated < index; allocated++) free(result[allocated]);
            free(result);
            return NULL;
        }
    }
    *count_out = count;
    return result;
}

static void free_string_array(char **values, jsize count) {
    if (values == NULL) return;
    for (jsize index = 0; index < count; index++) free(values[index]);
    free(values);
}

JNIEXPORT jintArray JNICALL Java_com_khalinux_runtime_NativePty_spawn(
        JNIEnv *env, jclass clazz, jobjectArray arguments, jobjectArray environment,
        jint rows, jint columns) {
    (void)clazz;
    jsize argument_count = 0;
    jsize environment_count = 0;
    char **argv = copy_string_array(env, arguments, &argument_count);
    if (argv == NULL) return NULL;
    char **envp = copy_string_array(env, environment, &environment_count);
    if (envp == NULL) {
        free_string_array(argv, argument_count);
        return NULL;
    }
    if (argument_count == 0 || argv[0][0] != '/') {
        free_string_array(argv, argument_count);
        free_string_array(envp, environment_count);
        throw_named(env, "java/lang/IllegalArgumentException", "PTY executable path must be absolute");
        return NULL;
    }
    if (rows < 1 || rows > USHRT_MAX || columns < 1 || columns > USHRT_MAX) {
        free_string_array(argv, argument_count);
        free_string_array(envp, environment_count);
        throw_named(env, "java/lang/IllegalArgumentException", "invalid PTY window size");
        return NULL;
    }

    struct winsize window_size;
    memset(&window_size, 0, sizeof(window_size));
    window_size.ws_row = (unsigned short)rows;
    window_size.ws_col = (unsigned short)columns;
    sigset_t blocked_signals;
    sigset_t unblocked_signals;
    sigset_t previous_mask;
    struct sigaction default_action;
    sigfillset(&blocked_signals);
    sigemptyset(&unblocked_signals);
    memset(&default_action, 0, sizeof(default_action));
    default_action.sa_handler = SIG_DFL;
    sigemptyset(&default_action.sa_mask);
    if (sigprocmask(SIG_BLOCK, &blocked_signals, &previous_mask) < 0) {
        int saved_errno = errno;
        free_string_array(argv, argument_count);
        free_string_array(envp, environment_count);
        throw_io_error(env, "sigprocmask(block)", saved_errno);
        return NULL;
    }
    int master_fd = -1;
    pid_t child = forkpty(&master_fd, NULL, NULL, &window_size);
    if (child < 0) {
        int saved_errno = errno;
        (void)sigprocmask(SIG_SETMASK, &previous_mask, NULL);
        free_string_array(argv, argument_count);
        free_string_array(envp, environment_count);
        throw_io_error(env, "forkpty", saved_errno);
        return NULL;
    }
    if (child == 0) {
        for (int signal_number = 1; signal_number < NSIG; signal_number++) {
            if (signal_number == SIGKILL || signal_number == SIGSTOP) continue;
            (void)sigaction(signal_number, &default_action, NULL);
        }
        (void)sigprocmask(SIG_SETMASK, &unblocked_signals, NULL);
        execve(argv[0], argv, envp);
        static const char message[] = "KhaLinux: execve failed\r\n";
        (void)write(STDERR_FILENO, message, sizeof(message) - 1U);
        _exit(127);
    }

    if (sigprocmask(SIG_SETMASK, &previous_mask, NULL) < 0) {
        int saved_errno = errno;
        (void)kill(-child, SIGKILL);
        (void)kill(child, SIGKILL);
        while (waitpid(child, NULL, 0) < 0 && errno == EINTR) { }
        (void)close(master_fd);
        free_string_array(argv, argument_count);
        free_string_array(envp, environment_count);
        throw_io_error(env, "sigprocmask(restore)", saved_errno);
        return NULL;
    }

    free_string_array(argv, argument_count);
    free_string_array(envp, environment_count);
    int descriptor_flags = fcntl(master_fd, F_GETFD);
    if (descriptor_flags < 0 || fcntl(master_fd, F_SETFD, descriptor_flags | FD_CLOEXEC) < 0) {
        int saved_errno = errno;
        (void)kill(-child, SIGKILL);
        (void)kill(child, SIGKILL);
        while (waitpid(child, NULL, 0) < 0 && errno == EINTR) { }
        (void)close(master_fd);
        throw_io_error(env, "fcntl(FD_CLOEXEC)", saved_errno);
        return NULL;
    }

    jint values[2] = {(jint)master_fd, (jint)child};
    jintArray result = (*env)->NewIntArray(env, 2);
    if (result == NULL) {
        (void)kill(-child, SIGKILL);
        (void)kill(child, SIGKILL);
        while (waitpid(child, NULL, 0) < 0 && errno == EINTR) { }
        (void)close(master_fd);
        return NULL;
    }
    (*env)->SetIntArrayRegion(env, result, 0, 2, values);
    if ((*env)->ExceptionCheck(env)) {
        (void)kill(-child, SIGKILL);
        (void)kill(child, SIGKILL);
        while (waitpid(child, NULL, 0) < 0 && errno == EINTR) { }
        (void)close(master_fd);
        return NULL;
    }
    return result;
}

JNIEXPORT jint JNICALL Java_com_khalinux_runtime_NativePty_read(
        JNIEnv *env, jclass clazz, jint master_fd, jbyteArray buffer, jint offset, jint length) {
    (void)clazz;
    if (buffer == NULL || offset < 0 || length < 0 || length > MAX_IO_BYTES ||
        offset > (*env)->GetArrayLength(env, buffer) - length) {
        throw_named(env, "java/lang/IllegalArgumentException", "invalid PTY read buffer range");
        return -1;
    }
    if (length == 0) return 0;
    char *temporary = (char *)malloc((size_t)length);
    if (temporary == NULL) {
        throw_named(env, "java/lang/OutOfMemoryError", "unable to allocate PTY read buffer");
        return -1;
    }
    ssize_t count;
    do {
        count = read(master_fd, temporary, (size_t)length);
    } while (count < 0 && errno == EINTR);
    if (count < 0 && errno == EIO) {
        free(temporary);
        return -1;
    }
    if (count < 0) {
        int saved_errno = errno;
        free(temporary);
        throw_io_error(env, "read(PTY master)", saved_errno);
        return -1;
    }
    if (count > 0) {
        (*env)->SetByteArrayRegion(env, buffer, offset, (jsize)count, (const jbyte *)temporary);
    }
    free(temporary);
    return (jint)count;
}

JNIEXPORT jint JNICALL Java_com_khalinux_runtime_NativePty_write(
        JNIEnv *env, jclass clazz, jint master_fd, jbyteArray buffer, jint offset, jint length) {
    (void)clazz;
    if (buffer == NULL || offset < 0 || length < 0 || length > MAX_IO_BYTES ||
        offset > (*env)->GetArrayLength(env, buffer) - length) {
        throw_named(env, "java/lang/IllegalArgumentException", "invalid PTY write buffer range");
        return -1;
    }
    if (length == 0) return 0;
    char *temporary = (char *)malloc((size_t)length);
    if (temporary == NULL) {
        throw_named(env, "java/lang/OutOfMemoryError", "unable to allocate PTY write buffer");
        return -1;
    }
    (*env)->GetByteArrayRegion(env, buffer, offset, length, (jbyte *)temporary);
    if ((*env)->ExceptionCheck(env)) {
        free(temporary);
        return -1;
    }
    jint total = 0;
    while (total < length) {
        ssize_t count = write(master_fd, temporary + total, (size_t)(length - total));
        if (count < 0 && errno == EINTR) continue;
        if (count < 0) {
            int saved_errno = errno;
            free(temporary);
            throw_io_error(env, "write(PTY master)", saved_errno);
            return -1;
        }
        if (count == 0) {
            free(temporary);
            throw_io_error(env, "write(PTY master)", EIO);
            return -1;
        }
        total += (jint)count;
    }
    free(temporary);
    return total;
}

JNIEXPORT jboolean JNICALL Java_com_khalinux_runtime_NativePty_resize(
        JNIEnv *env, jclass clazz, jint master_fd, jint rows, jint columns) {
    (void)clazz;
    if (rows < 1 || rows > USHRT_MAX || columns < 1 || columns > USHRT_MAX) {
        throw_named(env, "java/lang/IllegalArgumentException", "invalid PTY window size");
        return JNI_FALSE;
    }
    struct winsize window_size;
    memset(&window_size, 0, sizeof(window_size));
    window_size.ws_row = (unsigned short)rows;
    window_size.ws_col = (unsigned short)columns;
    if (ioctl(master_fd, TIOCSWINSZ, &window_size) < 0) {
        if (errno == EIO || errno == EBADF) return JNI_FALSE;
        throw_io_error(env, "ioctl(TIOCSWINSZ)", errno);
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL Java_com_khalinux_runtime_NativePty_waitFor(
        JNIEnv *env, jclass clazz, jint process_id) {
    (void)clazz;
    int status = 0;
    pid_t result;
    do {
        result = waitpid((pid_t)process_id, &status, 0);
    } while (result < 0 && errno == EINTR);
    if (result < 0) {
        throw_io_error(env, "waitpid", errno);
        return -1;
    }
    if (WIFEXITED(status)) return (jint)WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return (jint)(128 + WTERMSIG(status));
    return 255;
}

JNIEXPORT jboolean JNICALL Java_com_khalinux_runtime_NativePty_signal(
        JNIEnv *env, jclass clazz, jint process_id, jboolean force) {
    (void)clazz;
    int signal_number = force == JNI_TRUE ? SIGKILL : SIGTERM;
    if (kill(-(pid_t)process_id, signal_number) == 0) return JNI_TRUE;
    int saved_errno = errno;
    if (saved_errno == ESRCH && kill((pid_t)process_id, signal_number) == 0) return JNI_TRUE;
    if (errno == ESRCH) return JNI_FALSE;
    throw_io_error(env, "kill(PTY process group)", errno);
    return JNI_FALSE;
}

JNIEXPORT void JNICALL Java_com_khalinux_runtime_NativePty_close(
        JNIEnv *env, jclass clazz, jint file_descriptor) {
    (void)env;
    (void)clazz;
    (void)close(file_descriptor);
}
