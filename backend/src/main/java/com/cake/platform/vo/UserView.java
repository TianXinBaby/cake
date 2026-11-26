package com.cake.platform.vo;

import java.time.LocalDateTime;
import com.cake.platform.entity.UserAccount;
import com.cake.platform.security.AuthPrincipal;
import com.fasterxml.jackson.annotation.JsonInclude;

public record UserView(String id, String username, String nickname, String phone, String role,
        int status, LocalDateTime createTime,
        @JsonInclude(JsonInclude.Include.NON_NULL) String merchantId) {
    public static UserView from(UserAccount user, AuthPrincipal principal) {
        return new UserView(user.id().toString(), user.username(), user.nickname(), user.phone(),
                user.role(), user.status(), user.createTime(),
                principal.merchantId() == null ? null : principal.merchantId().toString());
    }
}
