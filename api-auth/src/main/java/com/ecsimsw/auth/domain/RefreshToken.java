package com.ecsimsw.auth.domain;

import com.ecsimsw.common.error.AuthException;
import com.ecsimsw.common.error.ErrorType;
import com.ecsimsw.domain.User;
import com.ecsimsw.domain.support.JwtUtils;

import java.util.Map;

import static com.ecsimsw.common.config.TokenConfig.REFRESH_TOKEN_EXPIRED_TIME;

public record RefreshToken(
    String username
) {
    public static RefreshToken of(User user) {
        return new RefreshToken(user.getUsername());
    }

    public static RefreshToken fromToken(String secretKey, String token) {
        try {
            return new RefreshToken(JwtUtils.getClaimValue(secretKey, token, "username"));
        } catch (Exception e) {
            throw new AuthException(ErrorType.INVALID_TOKEN);
        }
    }

    public String asJwtToken(String secretKey) {
        return JwtUtils.generate(secretKey, REFRESH_TOKEN_EXPIRED_TIME, Map.of("username", username));
    }
}
