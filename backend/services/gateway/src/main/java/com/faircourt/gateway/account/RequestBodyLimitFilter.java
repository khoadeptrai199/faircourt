package com.faircourt.gateway.account;

import com.faircourt.common.web.ApiError;
import com.faircourt.common.web.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Caps the body of the anonymous login and password reset requests (OWASP: an unauthenticated route must not let anyone make the server
 * read huge bodies). A login body is a few hundred bytes; anything over the limit is refused with 400 before it is
 * parsed. Works for chunked bodies too: the stream is counted while it is read.
 */
public class RequestBodyLimitFilter extends OncePerRequestFilter {

    /** Enough for an email of 254 characters and a password of 72 bytes, with JSON overhead and escapes. */
    public static final int MAX_BODY_BYTES = 4096;

    private final Set<String> paths;

    public RequestBodyLimitFilter(String... paths) {
        this.paths = Set.of(paths);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !paths.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            ErrorCode code = ErrorCode.VALIDATION_FAILED;
            response.setStatus(code.status().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getOutputStream()
                    .write(ApiError.of(code, code.defaultMessage()).toJson().getBytes(StandardCharsets.UTF_8));
            return;
        }
        chain.doFilter(new LimitedRequest(request), response);
    }

    /** Fails the read (the body then counts as unreadable, answered 400) once more than the limit has arrived. */
    private static final class LimitedRequest extends HttpServletRequestWrapper {

        LimitedRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            ServletInputStream delegate = super.getInputStream();
            return new ServletInputStream() {
                private long read;

                private void count(long n) throws IOException {
                    read += n;
                    if (read > MAX_BODY_BYTES) {
                        throw new IOException("Request body too large");
                    }
                }

                @Override
                public int read() throws IOException {
                    int b = delegate.read();
                    if (b >= 0) {
                        count(1);
                    }
                    return b;
                }

                @Override
                public int read(byte[] buffer, int off, int len) throws IOException {
                    int n = delegate.read(buffer, off, len);
                    if (n > 0) {
                        count(n);
                    }
                    return n;
                }

                @Override
                public boolean isFinished() {
                    return delegate.isFinished();
                }

                @Override
                public boolean isReady() {
                    return delegate.isReady();
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    delegate.setReadListener(listener);
                }
            };
        }
    }
}
