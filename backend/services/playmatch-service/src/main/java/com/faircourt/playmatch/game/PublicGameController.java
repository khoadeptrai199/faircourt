package com.faircourt.playmatch.game;

import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/v1/public/games/{publicCode}}: anyone; read-only public summary (blueprint section 13, OD-22). */
@RestController
public class PublicGameController {

    private static final Pattern CODE = Pattern.compile("[A-Z0-9]{1,20}");

    private final PublicGameQuery query;

    public PublicGameController(PublicGameQuery query) {
        this.query = query;
    }

    @GetMapping("/api/v1/public/games/{publicCode}")
    public PublicGameResponse get(@PathVariable String publicCode) {
        String code = publicCode.strip().toUpperCase(Locale.ROOT);
        if (!CODE.matcher(code).matches()) {
            // Malformed codes never reach the database.
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        return query.findByPublicCode(code).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }
}
