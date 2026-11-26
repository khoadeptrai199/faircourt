package com.faircourt.common.identity;

/** Name of the internal header that carries the identity token from the gateway to a service. */
public final class IdentityTokenHeader {

    /** The prefix {@code X-Internal-} is stripped from browser requests by the gateway, so only it can set this. */
    public static final String NAME = "X-Internal-Identity";

    private IdentityTokenHeader() {}
}
