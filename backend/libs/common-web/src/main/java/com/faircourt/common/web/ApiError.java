package com.faircourt.common.web;

/** Error body of every API error (blueprint section 13): {@code {"code","message","requestId"}}. */
public record ApiError(String code, String message, String requestId) {

    public static ApiError of(ErrorCode code, String message) {
        return new ApiError(code.name(), message, RequestIdFilter.currentRequestId());
    }

    /** JSON without a JSON library, for the security handlers that write the response themselves. */
    public String toJson() {
        return "{\"code\":" + quote(code) + ",\"message\":" + quote(message) + ",\"requestId\":" + quote(requestId)
                + "}";
    }

    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
