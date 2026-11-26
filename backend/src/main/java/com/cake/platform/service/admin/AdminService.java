package com.cake.platform.service.admin;

import java.math.BigInteger;
import java.util.List;
import com.cake.platform.dto.AdminRequests;
import com.cake.platform.exception.AdminErrorCode;
import com.cake.platform.exception.BusinessException;
import com.cake.platform.exception.CommonErrorCode;
import com.cake.platform.mapper.AdminMapper;
import com.cake.platform.mapper.UserAuthMapper;
import com.cake.platform.security.AuthErrorCode;
import com.cake.platform.security.AuthFailure;
import com.cake.platform.security.AuthPrincipal;
import com.cake.platform.vo.*;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile({"dev", "prod", "mysql-test"})
public class AdminService {
    private final AdminMapper mapper;
    private final UserAuthMapper users;
    private final PasswordEncoder passwords;
    private final TransactionTemplate writes;
    private final TransactionTemplate reads;

    public AdminService(AdminMapper mapper, UserAuthMapper users, PasswordEncoder passwords,
                        PlatformTransactionManager manager) {
        this.mapper = mapper; this.users = users; this.passwords = passwords;
        writes = new TransactionTemplate(manager);
        reads = new TransactionTemplate(manager);
        reads.setReadOnly(true);
    }

    public PageView<MerchantView> merchants(AdminRequests.ListQuery query) {
        return reads.execute(tx -> new PageView<>(query.offset() == null ? List.of()
                : mapper.merchants(query, query.offset()).stream().map(MerchantView::from).toList(),
                mapper.merchantCount(query), query.page(), query.size()));
    }

    public MerchantView create(AuthPrincipal admin, AdminRequests.Creation input) {
        String hash = passwords.encode(input.password());
        try {
            return writes.execute(tx -> {
                requireAdmin(admin);
                mapper.createUser(input, hash);
                var user = users.findByUsername(input.accountPhone());
                mapper.createMerchant(user.id(), input);
                return MerchantView.from(mapper.merchantByUser(user.id()));
            });
        } catch (DuplicateKeyException ignored) { throw new AuthFailure(AuthErrorCode.DUPLICATE); }
    }

    public MerchantView edit(AuthPrincipal admin, BigInteger id, AdminRequests.Edit input) {
        return writes.execute(tx -> {
            requireAdmin(admin);
            lockMerchant(id);
            requireOne(mapper.editMerchant(id, input));
            return MerchantView.from(mapper.merchant(id));
        });
    }

    public MerchantView merchantStatus(AuthPrincipal admin, BigInteger id, int status) {
        return writes.execute(tx -> {
            requireAdmin(admin);
            lockMerchant(id);
            var merchant = mapper.merchant(id);
            if (merchant.status() != status) requireOne(mapper.merchantStatus(id, status));
            return MerchantView.from(mapper.merchant(id));
        });
    }

    public PageView<UserView> customers(AdminRequests.ListQuery query) {
        return reads.execute(tx -> new PageView<>(query.offset() == null ? List.of()
                : mapper.customers(query, query.offset()).stream().map(this::customerView).toList(),
                mapper.customerCount(query), query.page(), query.size()));
    }

    public UserView customerStatus(AuthPrincipal admin, BigInteger id, int status) {
        return writes.execute(tx -> {
            requireAdmin(admin);
            var user = mapper.lockUser(id);
            if (user == null || !user.role().equals("CUSTOMER")) throw new BusinessException(AdminErrorCode.USER_NOT_MANAGEABLE);
            if (user.status() != status) requireOne(mapper.customerStatus(id, status));
            return customerView(users.findById(id));
        });
    }

    private void requireAdmin(AuthPrincipal principal) {
        if (principal == null) throw new AuthFailure(AuthErrorCode.UNAUTHENTICATED);
        var current = mapper.lockUser(principal.userId());
        if (current == null) throw new AuthFailure(AuthErrorCode.UNAUTHENTICATED);
        if (current.status() != 1) throw new AuthFailure(AuthErrorCode.DISABLED);
        if (current.tokenVersion() != principal.tokenVersion()) throw new AuthFailure(AuthErrorCode.UNAUTHENTICATED);
        if (!current.role().equals("ADMIN")) throw new AuthFailure(AuthErrorCode.FORBIDDEN);
    }

    private void lockMerchant(BigInteger id) {
        if (mapper.lockMerchant(id) == null) throw new BusinessException(AdminErrorCode.MERCHANT_NOT_FOUND);
    }
    private UserView customerView(com.cake.platform.entity.UserAccount row) {
        return UserView.from(row, new AuthPrincipal(row.id(), "CUSTOMER", row.tokenVersion(), null));
    }
    private static void requireOne(int count) {
        if (count != 1) throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
    }
}
