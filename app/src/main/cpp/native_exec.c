#define _GNU_SOURCE

#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

#ifndef MFD_CLOEXEC
#define MFD_CLOEXEC 0x0001U
#endif

#ifndef AT_EMPTY_PATH
#define AT_EMPTY_PATH 0x1000
#endif

#ifndef __NR_memfd_create
#error "The NDK sysroot must define __NR_memfd_create for the selected ABI."
#endif

#ifndef __NR_execveat
#error "The NDK sysroot must define __NR_execveat for the selected ABI."
#endif

static void throw_io(JNIEnv *env, const char *message) {
    jclass type = (*env)->FindClass(env, "java/io/IOException");
    if (type != NULL) {
        (*env)->ThrowNew(env, type, message);
        (*env)->DeleteLocalRef(env, type);
    }
}

static char **read_string_array(JNIEnv *env, jobjectArray source, jsize *length) {
    *length = (*env)->GetArrayLength(env, source);
    char **values = calloc((size_t) *length + 1, sizeof(char *));
    if (values == NULL) return NULL;

    for (jsize i = 0; i < *length; i++) {
        jstring value = (jstring) (*env)->GetObjectArrayElement(env, source, i);
        if (value == NULL) {
            values[i] = NULL;
            continue;
        }
        const char *chars = (*env)->GetStringUTFChars(env, value, NULL);
        if (chars == NULL) {
            (*env)->DeleteLocalRef(env, value);
            return values;
        }
        values[i] = strdup(chars);
        (*env)->ReleaseStringUTFChars(env, value, chars);
        (*env)->DeleteLocalRef(env, value);
        if (values[i] == NULL) return values;
    }
    return values;
}

static void free_string_array(char **values) {
    if (values == NULL) return;
    for (size_t i = 0; values[i] != NULL; i++) free(values[i]);
    free(values);
}

static int stage_memfd(const char *path) {
    int source = open(path, O_RDONLY | O_CLOEXEC);
    if (source < 0) return -1;

    int target = (int) syscall(__NR_memfd_create, "tradepulse-runtime", MFD_CLOEXEC);
    if (target < 0) {
        close(source);
        return -1;
    }

    char buffer[65536];
    for (;;) {
        ssize_t count = read(source, buffer, sizeof(buffer));
        if (count == 0) break;
        if (count < 0) {
            if (errno == EINTR) continue;
            close(source);
            close(target);
            return -1;
        }

        ssize_t offset = 0;
        while (offset < count) {
            ssize_t written = write(target, buffer + offset, (size_t) (count - offset));
            if (written < 0) {
                if (errno == EINTR) continue;
                close(source);
                close(target);
                return -1;
            }
            offset += written;
        }
    }

    close(source);
    return target;
}

JNIEXPORT jint JNICALL
Java_com_example_mt5_NativeProcessLauncher_nativeStart(
        JNIEnv *env, jobject instance, jstring jPath, jobjectArray jArgv,
        jobjectArray jEnvp, jstring jWorkingDirectory, jstring jLogPath, jint sdkInt) {
    (void) instance;
    const char *path = (*env)->GetStringUTFChars(env, jPath, NULL);
    const char *working_directory = (*env)->GetStringUTFChars(env, jWorkingDirectory, NULL);
    const char *log_path = (*env)->GetStringUTFChars(env, jLogPath, NULL);
    if (path == NULL || working_directory == NULL || log_path == NULL) {
        throw_io(env, "Unable to read native runtime launch arguments.");
        goto fail_strings;
    }

    jsize argc = 0;
    jsize envc = 0;
    char **argv = read_string_array(env, jArgv, &argc);
    char **envp = read_string_array(env, jEnvp, &envc);
    if (argv == NULL || envp == NULL || argc == 0 || argv[0] == NULL) {
        throw_io(env, "Unable to allocate native runtime arguments.");
        free_string_array(argv);
        free_string_array(envp);
        goto fail_strings;
    }

    int executable_fd = -1;
    if (sdkInt >= 29) {
        executable_fd = stage_memfd(path);
        if (executable_fd < 0) {
            char message[256];
            snprintf(message, sizeof(message), "Unable to stage runtime executable: %s (errno=%d)", strerror(errno), errno);
            throw_io(env, message);
            free_string_array(argv);
            free_string_array(envp);
            goto fail_strings;
        }
    }

    int error_pipe[2];
    if (pipe2(error_pipe, O_CLOEXEC) != 0) {
        char message[256];
        snprintf(message, sizeof(message), "Unable to create runtime launch pipe: %s", strerror(errno));
        throw_io(env, message);
        if (executable_fd >= 0) close(executable_fd);
        free_string_array(argv);
        free_string_array(envp);
        goto fail_strings;
    }

    pid_t pid = fork();
    if (pid < 0) {
        char message[256];
        snprintf(message, sizeof(message), "Unable to fork runtime process: %s", strerror(errno));
        throw_io(env, message);
        close(error_pipe[0]);
        close(error_pipe[1]);
        if (executable_fd >= 0) close(executable_fd);
        free_string_array(argv);
        free_string_array(envp);
        goto fail_strings;
    }

    if (pid == 0) {
        close(error_pipe[0]);
        setpgid(0, 0);
        int log_fd = open(log_path, O_WRONLY | O_CREAT | O_APPEND, 0600);
        if (log_fd >= 0) {
            dup2(log_fd, STDOUT_FILENO);
            dup2(log_fd, STDERR_FILENO);
            close(log_fd);
        }
        chdir(working_directory);
        if (sdkInt >= 29) {
            syscall(__NR_execveat, executable_fd, "", argv, envp, AT_EMPTY_PATH);
        } else {
            execve(path, argv, envp);
        }
        int launch_error = errno;
        write(error_pipe[1], &launch_error, sizeof(launch_error));
        _exit(126);
    }

    close(error_pipe[1]);
    if (executable_fd >= 0) close(executable_fd);
    int launch_error = 0;
    ssize_t received = read(error_pipe[0], &launch_error, sizeof(launch_error));
    close(error_pipe[0]);
    free_string_array(argv);
    free_string_array(envp);

    if (received == (ssize_t) sizeof(launch_error)) {
        int status;
        waitpid(pid, &status, 0);
        char message[320];
        snprintf(message, sizeof(message), "Unable to execute runtime binary: %s (errno=%d)", strerror(launch_error), launch_error);
        throw_io(env, message);
        (*env)->ReleaseStringUTFChars(env, jPath, path);
        (*env)->ReleaseStringUTFChars(env, jWorkingDirectory, working_directory);
        (*env)->ReleaseStringUTFChars(env, jLogPath, log_path);
        return -1;
    }

    (*env)->ReleaseStringUTFChars(env, jPath, path);
    (*env)->ReleaseStringUTFChars(env, jWorkingDirectory, working_directory);
    (*env)->ReleaseStringUTFChars(env, jLogPath, log_path);
    return (jint) pid;

fail_strings:
    if (path != NULL) (*env)->ReleaseStringUTFChars(env, jPath, path);
    if (working_directory != NULL) (*env)->ReleaseStringUTFChars(env, jWorkingDirectory, working_directory);
    if (log_path != NULL) (*env)->ReleaseStringUTFChars(env, jLogPath, log_path);
    return -1;
}

JNIEXPORT jint JNICALL
Java_com_example_mt5_NativeProcessLauncher_nativeWaitFor(JNIEnv *env, jobject instance, jint pid) {
    (void) env;
    (void) instance;
    int status = 0;
    pid_t result;
    do {
        result = waitpid((pid_t) pid, &status, 0);
    } while (result < 0 && errno == EINTR);
    if (result < 0) return -1;
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return -1;
}

JNIEXPORT void JNICALL
Java_com_example_mt5_NativeProcessLauncher_nativeKill(JNIEnv *env, jobject instance, jint pid, jint signal_number) {
    (void) env;
    (void) instance;
    kill(-(pid_t) pid, signal_number);
    kill((pid_t) pid, signal_number);
}

JNIEXPORT jboolean JNICALL
Java_com_example_mt5_NativeProcessLauncher_nativeIsAlive(JNIEnv *env, jobject instance, jint pid) {
    (void) env;
    (void) instance;
    return kill((pid_t) pid, 0) == 0 || errno == EPERM ? JNI_TRUE : JNI_FALSE;
}
