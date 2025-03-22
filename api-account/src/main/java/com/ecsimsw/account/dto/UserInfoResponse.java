package com.ecsimsw.account.dto;

import com.ecsimsw.account.domain.User;
import com.ecsimsw.common.dto.ApiResult;

public record UserInfoResponse(
    String username,
    String email
) implements ApiResult {

    public static UserInfoResponse of(User user) {
        return new UserInfoResponse(
            user.getUsername(),
            user.getEmail()
        );
    }
}
