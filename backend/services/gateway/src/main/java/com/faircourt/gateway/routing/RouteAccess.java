package com.faircourt.gateway.routing;

/**
 * Who may call a route at the gateway (blueprint section 3). Only {@code PUBLIC} exists until Account login adds the
 * signed-in kinds.
 */
public enum RouteAccess {
    PUBLIC
}
