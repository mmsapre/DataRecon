package com.mms.data.recon.util;

public final class ThrowableUtils {
    private ThrowableUtils() {}

    public static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    public static String extractFailureCause(Throwable error) {
        if (error == null) {
            return null;
        }
        String top = messageOrType(error);
        Throwable root = rootCause(error);
        if (root == error) {
            return top;
        }
        return top + ", rootCause=[" + messageOrType(root) + "]";
    }

    public static String rootMessage(Throwable error) {
        Throwable root = rootCause(error);
        return root == null ? null : root.getMessage();
    }

    private static String messageOrType(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return message;
    }
}
