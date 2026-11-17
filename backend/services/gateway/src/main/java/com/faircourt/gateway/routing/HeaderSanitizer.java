package com.faircourt.gateway.routing;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.springframework.web.servlet.function.ServerRequest;

/**
 * Removes browser credentials and identity-like headers before a request is forwarded to a service (AD-14: "strips
 * client identity headers"; blueprint section 13: the client's own claims are never proof of identity).
 *
 * <ul>
 *   <li>{@code Cookie}: the gateway session stays at the gateway; services never see browser cookies.
 *   <li>{@code Authorization}, {@code Proxy-Authorization}: no client credentials reach a service.
 *   <li>{@code Forwarded}, {@code X-Forwarded-*}: a client must not choose the address or host a service sees.
 *   <li>{@code X-Internal-*}: reserved prefix for headers only the gateway may set (the identity token from Account
 *       login will use it); a client-sent one is always dropped.
 * </ul>
 *
 * {@code X-Request-Id} is replaced earlier by {@code RequestIdFilter}.
 */
public final class HeaderSanitizer implements Function<ServerRequest, ServerRequest> {

    public static final String INTERNAL_HEADER_PREFIX = "x-internal-";

    private static final List<String> REMOVED = List.of("cookie", "authorization", "proxy-authorization", "forwarded");

    @Override
    public ServerRequest apply(ServerRequest request) {
        return ServerRequest.from(request)
                .headers(headers -> {
                    for (String name : new ArrayList<>(headers.headerNames())) {
                        if (mustRemove(name)) {
                            headers.remove(name);
                        }
                    }
                })
                .build();
    }

    static boolean mustRemove(String headerName) {
        String name = headerName.toLowerCase(Locale.ROOT);
        return REMOVED.contains(name) || name.startsWith("x-forwarded-") || name.startsWith(INTERNAL_HEADER_PREFIX);
    }
}
