package com.faircourt.common.identity;

import java.util.UUID;

/**
 * Who is calling a service (blueprint section 13; AD-14). A service builds it only from the signed identity token the
 * gateway forwards, never from the path, body or any other header. A guest kind comes with the first forwarded guest
 * route.
 */
public sealed interface CallerIdentity {

    /** No identity token was sent. */
    record Anonymous() implements CallerIdentity {}

    /** A signed-in account. */
    record Account(UUID userId) implements CallerIdentity {}
}
