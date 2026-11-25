package com.faircourt.gateway.routing;

/**
 * Who may call a route at the gateway (blueprint section 3). {@code PUBLIC}: anyone. {@code ACCOUNT}: a signed-in
 * account (Account login). {@code PLAYER}: a signed-in account or a guest cookie (My attendance); the route's controller
 * does the real check because only it can read the cookie. Host kinds come with their features.
 */
public enum RouteAccess {
    PUBLIC,
    ACCOUNT,
    PLAYER
}
