package dev.settle.common;

import java.time.Duration;

public class TooManyRequestsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyRequestsException(Duration retryAfter) {
        super(message(retryAfter));
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }

    private static String message(Duration retryAfter) {
        long minutes = Math.max(1, (retryAfter.toSeconds() + 59) / 60);
        return "Too many failed sign-in attempts. Try again in %d %s."
                .formatted(minutes, minutes == 1 ? "minute" : "minutes");
    }
}
