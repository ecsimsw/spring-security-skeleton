package com.ecsimsw.auth.service;

import com.ecsimsw.auth.domain.*;
import com.ecsimsw.auth.domain.AccessToken;
import com.ecsimsw.auth.dto.LogInResponse;
import com.ecsimsw.auth.dto.Tokens;
import com.ecsimsw.common.config.TokenConfig;
import com.ecsimsw.common.domain.BlockedUser;
import com.ecsimsw.common.domain.BlockedUserRepository;
import com.ecsimsw.common.domain.RefreshTokenRepository;
import com.ecsimsw.common.client.dto.AuthCreationRequest;
import com.ecsimsw.common.error.AuthException;
import com.ecsimsw.common.error.ErrorType;
import com.ecsimsw.common.client.dto.AuthUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

@RequiredArgsConstructor
@Service
public class AuthService {

    @Value("${auth.token.secret}")
    private String secret;

    private final UserPasswordRepository userPasswordRepository;
    private final UserRoleRepository userRoleRepository;
    private final BlockedUserRepository blockedUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    public LogInResponse issue(String username) {
        var userPassword = userPasswordRepository.findByUsername(username).orElseThrow(() -> new AuthException(ErrorType.FAILED_TO_AUTHENTICATE));
        var userRole = userRoleRepository.findByUserId(userPassword.userId()).orElseThrow(() -> new AuthException(ErrorType.FAILED_TO_AUTHENTICATE));
        var tokens = createTokens(username, userRole.getIsAdmin());
        refreshTokenRepository.save(username, tokens.refreshToken());
        return new LogInResponse(tokens);
    }

    public LogInResponse reissue(String refreshToken) {
        var username = RefreshToken.fromToken(secret, refreshToken).username();
        var tokenOpt = refreshTokenRepository.findByUsername(username);
        if (tokenOpt.isEmpty()) {
            throw new AuthException(ErrorType.INVALID_TOKEN);
        }
        return issue(username);
    }

    public Tokens createTokens(String username, boolean isAdmin) {
        return new Tokens(
            new AccessToken(username, isAdmin).asJwtToken(secret),
            new RefreshToken(username).asJwtToken(secret)
        );
    }

    public void blockToken(String token) {
        blockedUserRepository.save(new BlockedUser(token));
    }

    @Transactional(readOnly = true)
    public List<String> roleNames(String username) {
        var userPassword = userPasswordRepository.findByUsername(username).orElseThrow(() -> new AuthException(ErrorType.FAILED_TO_AUTHENTICATE));
        var userRole = userRoleRepository.findByUserId(userPassword.userId()).orElseThrow(() -> new AuthException(ErrorType.FAILED_TO_AUTHENTICATE));
        if (userRole.getIsAdmin()) {
            return List.of("ADMIN");
        }
        return userRole.roleNames();
    }

    @Transactional
    public void createUserAuth(AuthCreationRequest request) {
        var userPassword = new UserPassword(passwordEncoder, request.userId(), request.username(), request.purePassword());
        userPasswordRepository.save(userPassword);
        var userRole = new UserRole(request.userId(), false, new HashSet<>());
        userRoleRepository.save(userRole);
    }

    public void updateUserAuth(AuthUpdateRequest request) {
        var userPassword = userPasswordRepository.findByUsername(request.username()).orElseThrow(() -> new AuthException(ErrorType.FAILED_TO_AUTHENTICATE));
        userPassword.updatePassword(passwordEncoder, request.newPassword());
    }
}
