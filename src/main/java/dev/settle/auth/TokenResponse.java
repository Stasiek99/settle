package dev.settle.auth;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

    @Override
    public String toString() {
        return "TokenResponse[accessToken=***, tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
    }
}
