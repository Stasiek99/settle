package dev.settle.common;

public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException() {
        super("Invalid email or password");
    }

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
