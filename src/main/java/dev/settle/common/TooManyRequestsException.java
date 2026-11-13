package dev.settle.common;

import java.time.Duration;

public class TooManyRequestsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyRequestsException(Duration retryAfter) {
        this("Too many failed sign-in attempts", retryAfter);
    }

    public TooManyRequestsException(String reason, Duration retryAfter) {
        super(message(reason, retryAfter));
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }

    private static String message(String reason, Duration retryAfter) {
        long minutes = Math.max(1, (retryAfter.toSeconds() + 59) / 60);
        return "%s. Try again in %d %s.".formatted(reason, minutes, minutes == 1 ? "minute" : "minutes");
    }
}
