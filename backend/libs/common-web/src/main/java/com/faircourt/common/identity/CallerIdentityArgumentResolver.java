package com.faircourt.common.identity;

import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Fills a {@link CallerIdentity} controller argument from the internal identity header, and from nothing else (AD-14).
 * No header means {@link CallerIdentity.Anonymous}; a header that is not a valid token means 401 {@code UNAUTHENTICATED}
 * (logged as one line without the token). The token must be issued for this service, the request method and the request
 * path (Attendance management design 3.2).
 */
public final class CallerIdentityArgumentResolver implements HandlerMethodArgumentResolver {

    private static final Logger log = LoggerFactory.getLogger(CallerIdentityArgumentResolver.class);

    private final IdentityToken tokens;
    private final String serviceName;

    public CallerIdentityArgumentResolver(IdentityToken tokens, String serviceName) {
        this.tokens = tokens;
        this.serviceName = serviceName;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CallerIdentity.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        String[] values = webRequest.getHeaderValues(IdentityTokenHeader.NAME);
        if (values == null || values.length == 0) {
            return new CallerIdentity.Anonymous();
        }
        if (values.length != 1) {
            log.warn("Identity token refused: more than one header value");
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        CallerIdentity.Account account = request == null
                ? null
                : tokens.verify(values[0], serviceName, request.getMethod(), request.getRequestURI())
                        .orElse(null);
        if (account == null) {
            log.warn("Identity token refused: invalid or expired");
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        return account;
    }
}
