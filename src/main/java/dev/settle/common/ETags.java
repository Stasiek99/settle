package dev.settle.common;

public final class ETags {

    private ETags() {
    }

    public static long requireVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException("The If-Match header with the current ETag is required");
        }
        String value = ifMatch.trim();
        if (value.length() < 3 || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"') {
            throw new InvalidRequestException("If-Match must be a single strong ETag in double quotes");
        }
        try {
            long version = Long.parseLong(value.substring(1, value.length() - 1));
            if (version < 0) {
                throw new NumberFormatException(value);
            }
            return version;
        } catch (NumberFormatException e) {
            throw new InvalidRequestException("If-Match does not hold a valid ETag");
        }
    }
}
