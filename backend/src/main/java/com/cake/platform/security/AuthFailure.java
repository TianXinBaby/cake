package com.cake.platform.security;

import java.time.LocalDateTime;
import com.cake.platform.exception.BusinessException;

public final class AuthFailure extends BusinessException {
    private final LocalDateTime lockedUntil;
    public AuthFailure(AuthErrorCode code) { this(code, null); }
    public AuthFailure(AuthErrorCode code, LocalDateTime lockedUntil) {
        super(code); this.lockedUntil = lockedUntil;
    }
    public LocalDateTime lockedUntil() { return lockedUntil; }
}
