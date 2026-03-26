package com.portfolio.aicontentstudio.modules.auth.service;

import com.portfolio.aicontentstudio.config.properties.RefreshTokenCookieProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class RefreshTokenCookieServiceImpl implements RefreshTokenCookieService {

    private final RefreshTokenCookieProperties cookieProperties;

    @Override
    public String buildRefreshTokenCookie(String refreshToken) {
        ResponseCookie.ResponseCookieBuilder cookieBuilder = ResponseCookie.from(cookieProperties.getName(), refreshToken)
                .httpOnly(true)
                .secure(cookieProperties.isSecure())
                .path(cookieProperties.getPath())
                .sameSite(cookieProperties.getSameSite())
                .maxAge(cookieProperties.getMaxAgeSeconds());

        if (StringUtils.hasText(cookieProperties.getDomain())) {
            cookieBuilder.domain(cookieProperties.getDomain());
        }

        return cookieBuilder.build().toString();
    }

    @Override
    public String clearRefreshTokenCookie() {
        ResponseCookie.ResponseCookieBuilder cookieBuilder = ResponseCookie.from(cookieProperties.getName(), "")
                .httpOnly(true)
                .secure(cookieProperties.isSecure())
                .path(cookieProperties.getPath())
                .sameSite(cookieProperties.getSameSite())
                .maxAge(0);

        if (StringUtils.hasText(cookieProperties.getDomain())) {
            cookieBuilder.domain(cookieProperties.getDomain());
        }

        return cookieBuilder.build().toString();
    }
}
