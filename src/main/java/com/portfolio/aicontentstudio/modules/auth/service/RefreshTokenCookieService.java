package com.portfolio.aicontentstudio.modules.auth.service;

public interface RefreshTokenCookieService {

    String buildRefreshTokenCookie(String refreshToken);

    String clearRefreshTokenCookie();
}
