package com.faircourt.gateway.routing;

import com.faircourt.common.identity.IdentityToken;
import com.faircourt.common.identity.IdentityTokenHeader;
import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import com.faircourt.gateway.account.AccountResponse;
import java.util.function.Function;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.function.ServerRequest;

/**
 * Adds the signed identity token ({@code X-Internal-Identity}) for the signed-in account to a forwarded request (AD-14;
 * Host board design 3.4). The token is bound to the route's target service, the request method and path (Attendance
 * management design 3.2). Runs after {@link HeaderSanitizer}, which has already dropped any client-sent copy. It does
 * nothing for routes that do not need the caller ({@code enabled == false}), so public routes never carry a token.
 */
public final class IdentityTokenFilter implements Function<ServerRequest, ServerRequest> {

    private final IdentityToken tokens;
    private final boolean enabled;
    private final String audience;

    public IdentityTokenFilter(IdentityToken tokens, boolean enabled, String audience) {
        this.tokens = tokens;
        this.enabled = enabled;
        this.audience = audience;
    }

    @Override
    public ServerRequest apply(ServerRequest request) {
        if (!enabled) {
            return request;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AccountResponse account)) {
            // The route rule already required an account; never forward without one.
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        String token;
        try {
            token = tokens.issue(
                    account.userId(),
                    audience,
                    request.method().name(),
                    request.uri().getRawPath());
        } catch (IllegalArgumentException e) {
            // A path the token cannot carry (over 512 chars or unusual characters) is not a route of ours.
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        return ServerRequest.from(request)
                .headers(headers -> headers.set(IdentityTokenHeader.NAME, token))
                .build();
    }
}
