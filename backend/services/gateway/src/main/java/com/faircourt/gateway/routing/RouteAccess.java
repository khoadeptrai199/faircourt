package com.faircourt.gateway.routing;

/**
 * Who may call a route at the gateway (blueprint section 3). {@code PUBLIC}: anyone. {@code ACCOUNT}: a signed-in
 * account (Account login). Guest and host kinds come with their features.
 */
public enum RouteAccess {
    PUBLIC,
    ACCOUNT
}
