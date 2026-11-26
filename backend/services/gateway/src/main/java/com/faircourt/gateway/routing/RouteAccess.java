package com.faircourt.gateway.routing;

/**
 * Who may call a route at the gateway (blueprint section 3). {@code PUBLIC}: anyone. {@code ACCOUNT}: a signed-in
 * account (Account login). {@code PLAYER}: a signed-in account or a guest cookie (My attendance); the route's controller
 * does the real check because only it can read the cookie. {@code HOST}: a signed-in account; the gateway adds the
 * identity token and the service checks that the account hosts the game (Host board).
 */
public enum RouteAccess {
    PUBLIC,
    ACCOUNT,
    PLAYER,
    HOST
}
