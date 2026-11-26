package com.cake.platform.service.auth;

import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.Set;
import com.cake.platform.entity.UserAccount;
import com.cake.platform.exception.BusinessException;
import com.cake.platform.exception.CommonErrorCode;
import com.cake.platform.exception.FieldValidationException;
import com.cake.platform.mapper.UserAuthMapper;
import com.cake.platform.security.*;
import com.cake.platform.vo.UserView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile({"dev", "prod", "mysql-test"})
public class AuthenticationService {
    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationService.class);
    private static final Set<String> ROLES = Set.of("ADMIN", "MERCHANT", "CUSTOMER");
    private final UserAuthMapper mapper;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final TransactionTemplate transactions;
    private final String dummyHash;

    public AuthenticationService(UserAuthMapper mapper, PasswordEncoder passwords, JwtService jwt,
                                 PlatformTransactionManager manager) {
        this.mapper = mapper; this.passwords = passwords; this.jwt = jwt;
        this.transactions = new TransactionTemplate(manager);
        // Same costly password operation for absent users; random dummy is never an account credential.
        dummyHash = passwords.encode(java.util.UUID.randomUUID().toString());
    }

    public UserView register(String phone, String nickname, String password) {
        String hash = passwords.encode(password);
        try {
            return transactions.execute(tx -> {
                mapper.register(phone, nickname, hash);
                var row = mapper.findByUsername(phone);
                return UserView.from(row, identity(row));
            });
        } catch (DuplicateKeyException ignored) { throw new AuthFailure(AuthErrorCode.DUPLICATE); }
    }

    public LoginView login(String username, String password) {
        // A failure result leaves the transaction normally so failure counts and locks are committed.
        LoginOutcome outcome = transactions.execute(tx -> {
            UserAccount row = mapper.lockByUsername(username);
            if (row == null) {
                passwords.matches(password, dummyHash);
                return LoginOutcome.failed(new AuthFailure(AuthErrorCode.BAD_CREDENTIALS));
            }
            if (row.status() != 1) { return LoginOutcome.failed(new AuthFailure(AuthErrorCode.DISABLED)); }
            var now = mapper.now();
            if (row.lockedUntil() != null && now.isBefore(row.lockedUntil())) {
                return LoginOutcome.failed(new AuthFailure(AuthErrorCode.LOCKED, row.lockedUntil()));
            }
            int failures = row.lockedUntil() == null ? row.loginFailCount() : 0;
            if (!passwords.matches(password, row.password())) {
                int count = Math.min(failures + 1, 5);
                var until = count == 5 ? mapper.now().plusMinutes(10) : null;
                mapper.failedLogin(row.id(), count, until);
                return LoginOutcome.failed(new AuthFailure(count == 5 ? AuthErrorCode.LOCKED
                        : AuthErrorCode.BAD_CREDENTIALS, until));
            }
            AuthPrincipal principal = identity(row);
            mapper.resetLogin(row.id());
            var token = jwt.issue(principal);
            return new LoginOutcome(new LoginView(token.token(), "Bearer", token.expiresAt(),
                    UserView.from(row, principal)), null);
        });
        if (outcome.error() != null) { throw outcome.error(); }
        return outcome.value();
    }

    public AuthPrincipal authenticate(String token) {
        BigInteger id;
        int version;
        try {
            var claims = jwt.decode(token);
            if (claims.getSubject() == null || !claims.getSubject().matches("[1-9][0-9]{0,19}")) {
                throw new IllegalArgumentException();
            }
            id = new BigInteger(claims.getSubject());
            if (id.bitLength() > 64 || !ROLES.contains(claims.getClaimAsString("role"))) {
                throw new IllegalArgumentException();
            }
            Object ver = claims.getClaims().get("ver");
            if (!(ver instanceof Number number)) { throw new IllegalArgumentException(); }
            var exact = new java.math.BigDecimal(number.toString());
            version = exact.intValueExact();
            if (version < 0) { throw new IllegalArgumentException(); }
        } catch (RuntimeException ignored) {
            throw new AuthFailure(AuthErrorCode.UNAUTHENTICATED);
        }
        var row = mapper.findById(id);
        requireActiveVersion(row, version);
        return identity(row);
    }

    public UserView me(AuthPrincipal principal) {
        var row = mapper.findById(principal.userId());
        requireActiveVersion(row, principal.tokenVersion());
        return UserView.from(row, identity(row));
    }

    public void logout(AuthPrincipal principal) {
        transactions.executeWithoutResult(tx -> {
            var row = currentLocked(principal);
            mapper.logout(row.id());
        });
    }

    public UserView nickname(AuthPrincipal principal, String nickname) {
        return transactions.execute(tx -> {
            var row = currentLocked(principal);
            mapper.nickname(row.id(), nickname);
            return UserView.from(mapper.findById(row.id()), identity(row));
        });
    }

    public void password(AuthPrincipal principal, String oldPassword, String newPassword) {
        transactions.executeWithoutResult(tx -> {
            var row = currentLocked(principal);
            if (!passwords.matches(oldPassword, row.password())) { throw new AuthFailure(AuthErrorCode.BAD_CREDENTIALS); }
            if (passwords.matches(newPassword, row.password())) {
                throw FieldValidationException.forField("newPassword", "新密码不能与原密码相同");
            }
            mapper.password(row.id(), passwords.encode(newPassword));
        });
    }

    private UserAccount currentLocked(AuthPrincipal principal) {
        var row = mapper.lockById(principal.userId());
        requireActiveVersion(row, principal.tokenVersion());
        identity(row);
        return row;
    }

    private void requireActiveVersion(UserAccount row, int version) {
        if (row == null) { throw new AuthFailure(AuthErrorCode.UNAUTHENTICATED); }
        if (row.status() != 1) { throw new AuthFailure(AuthErrorCode.DISABLED); }
        if (row.tokenVersion() != version) { throw new AuthFailure(AuthErrorCode.UNAUTHENTICATED); }
    }

    private AuthPrincipal identity(UserAccount row) {
        if (!ROLES.contains(row.role())) { throw integrity(); }
        var merchants = mapper.merchantIds(row.id());
        BigInteger merchantId = null;
        if (row.role().equals("MERCHANT")) {
            if (merchants.size() != 1) { throw integrity(); }
            merchantId = merchants.get(0);
        } else if (!merchants.isEmpty()) { throw integrity(); }
        return new AuthPrincipal(row.id(), row.role(), row.tokenVersion(), merchantId);
    }

    private BusinessException integrity() {
        LOG.error("authentication outcome=ACCOUNT_BINDING_INTEGRITY_ERROR");
        return new BusinessException(CommonErrorCode.INTERNAL_ERROR);
    }

    public record LoginView(String token, String tokenType, OffsetDateTime expiresAt, UserView user) { }
    private record LoginOutcome(LoginView value, AuthFailure error) {
        static LoginOutcome failed(AuthFailure error) { return new LoginOutcome(null, error); }
    }
}
