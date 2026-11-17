package com.faircourt.gateway.routing;

/** Test access to the package-private header rule. */
public final class HeaderSanitizerAccess {

    private HeaderSanitizerAccess() {}

    public static boolean mustRemove(String headerName) {
        return HeaderSanitizer.mustRemove(headerName);
    }
}
