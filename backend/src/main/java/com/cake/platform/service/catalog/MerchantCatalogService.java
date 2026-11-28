package com.cake.platform.service.catalog;

import java.math.BigInteger;
import java.util.List;
import com.cake.platform.dto.CatalogRequests;
import com.cake.platform.exception.BusinessException;
import com.cake.platform.exception.CatalogErrorCode;
import com.cake.platform.exception.CommonErrorCode;
import com.cake.platform.mapper.CatalogMapper;
import com.cake.platform.security.AuthErrorCode;
import com.cake.platform.security.AuthFailure;
import com.cake.platform.security.AuthPrincipal;
import com.cake.platform.vo.DeletionView;
import com.cake.platform.vo.MerchantCategoryView;
import com.cake.platform.vo.MerchantProductView;
import com.cake.platform.vo.PageView;
import com.cake.platform.vo.StockView;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Own-store product, category and stock maintenance (API-03~05, 17~22, 29~31).
 * Every statement uses the merchant bound to the authenticated account, never a request value.
 */
@Service
@Profile({"dev", "prod", "mysql-test"})
public class MerchantCatalogService {
    private final CatalogMapper mapper;
    private final TransactionTemplate writes;
    private final TransactionTemplate reads;

    public MerchantCatalogService(CatalogMapper mapper, PlatformTransactionManager manager) {
        this.mapper = mapper;
        writes = new TransactionTemplate(manager);
        reads = new TransactionTemplate(manager);
        reads.setReadOnly(true);
    }

    public PageView<MerchantProductView> products(AuthPrincipal principal, CatalogRequests.ProductQuery query) {
        return reads.execute(status -> {
            BigInteger merchantId = boundMerchant(principal);
            List<MerchantProductView> records = query.offset() == null ? List.of()
                    : mapper.merchantProducts(query, merchantId, query.offset()).stream()
                            .map(MerchantProductView::from).toList();
            return new PageView<>(records, mapper.merchantProductCount(query, merchantId),
                    query.page(), query.size());
        });
    }

    public MerchantProductView product(AuthPrincipal principal, BigInteger id) {
        return reads.execute(status -> requireProduct(boundMerchant(principal), id));
    }

    public MerchantProductView create(AuthPrincipal principal, CatalogRequests.ProductCreation input) {
        return writes.execute(status -> {
            BigInteger merchantId = requireMerchant(principal);
            if (mapper.lockCategory(input.categoryId(), merchantId) == null) {
                throw new BusinessException(CatalogErrorCode.CATEGORY_NOT_FOUND);
            }
            mapper.insertProduct(merchantId, input);
            return requireProduct(merchantId, mapper.lastInsertId());
        });
    }

    public MerchantProductView edit(AuthPrincipal principal, BigInteger id, CatalogRequests.ProductEdit input) {
        return writes.execute(status -> {
            BigInteger merchantId = requireMerchant(principal);
            if (mapper.lockCategory(input.categoryId(), merchantId) == null) {
                throw new BusinessException(CatalogErrorCode.CATEGORY_NOT_FOUND);
            }
            var targetCategory = mapper.category(input.categoryId(), merchantId);
            if (mapper.lockProduct(id, merchantId) == null) {
                throw new BusinessException(CatalogErrorCode.PRODUCT_NOT_FOUND);
            }
            var current = requireProduct(merchantId, id);
            // BR-10: a listed product may only move to an enabled category; edit never changes stock or status.
            if (current.status() == 1 && targetCategory.status() != 1) {
                throw new BusinessException(CatalogErrorCode.CATEGORY_DISABLED);
            }
            requireOne(mapper.updateProduct(id, merchantId, input));
            return requireProduct(merchantId, id);
        });
    }

    public DeletionView delete(AuthPrincipal principal, BigInteger id) {
        return writes.execute(status -> {
            BigInteger merchantId = requireMerchant(principal);
            if (mapper.lockProduct(id, merchantId) == null) {
                throw new BusinessException(CatalogErrorCode.PRODUCT_NOT_FOUND);
            }
            // Covers cancelled orders too; the foreign key blocks a reference created after this check.
            if (mapper.productReferenceCount(id) > 0) {
                throw new BusinessException(CatalogErrorCode.PRODUCT_REFERENCED);
            }
            try {
                requireOne(mapper.deleteProduct(id, merchantId));
            } catch (DataIntegrityViolationException referenced) {
                throw new BusinessException(CatalogErrorCode.PRODUCT_REFERENCED);
            }
            return DeletionView.of(id);
        });
    }

    public StockView stock(AuthPrincipal principal, BigInteger id, CatalogRequests.StockAdjustment input) {
        return writes.execute(status -> {
            BigInteger merchantId = requireMerchant(principal);
            int updated = mapper.updateStock(id, merchantId, input.delta(), input.expectedStock());
            if (updated != 1) {
                if (mapper.productForMerchant(id, merchantId) == null) {
                    throw new BusinessException(CatalogErrorCode.PRODUCT_NOT_FOUND);
                }
                throw new BusinessException(CatalogErrorCode.STOCK_CHANGED);
            }
            var row = mapper.stock(id);
            return new StockView(id.toString(), row.stock(), row.updateTime());
        });
    }

    public MerchantProductView productStatus(AuthPrincipal principal, BigInteger id, int status) {
        return writes.execute(tx -> {
            BigInteger merchantId = requireMerchant(principal);
            // Lock order for product maintenance is category then product (BR-11).
            var snapshot = mapper.productForMerchant(id, merchantId);
            if (snapshot == null) { throw new BusinessException(CatalogErrorCode.PRODUCT_NOT_FOUND); }
            if (mapper.lockCategory(snapshot.categoryId(), merchantId) == null) {
                throw new BusinessException(CatalogErrorCode.CATEGORY_NOT_FOUND);
            }
            var category = mapper.category(snapshot.categoryId(), merchantId);
            if (mapper.lockProduct(id, merchantId) == null) {
                throw new BusinessException(CatalogErrorCode.PRODUCT_NOT_FOUND);
            }
            var current = mapper.productForMerchant(id, merchantId);
            if (!current.categoryId().equals(snapshot.categoryId())) {
                throw new BusinessException(CatalogErrorCode.PRODUCT_CHANGED);
            }
            if (current.status() == status) { return MerchantProductView.from(current); }
            if (status == 1 && category.status() != 1) {
                throw new BusinessException(CatalogErrorCode.CATEGORY_DISABLED);
            }
            requireOne(mapper.updateProductStatus(id, merchantId, status));
            return MerchantProductView.from(mapper.productForMerchant(id, merchantId));
        });
    }

    public List<MerchantCategoryView> categories(AuthPrincipal principal, Integer status) {
        return reads.execute(tx -> mapper.merchantCategories(boundMerchant(principal), status).stream()
                .map(MerchantCategoryView::from).toList());
    }

    public MerchantCategoryView createCategory(AuthPrincipal principal, CatalogRequests.CategoryCreation input) {
        return writes.execute(tx -> {
            BigInteger merchantId = requireMerchant(principal);
            if (mapper.otherCategoryId(merchantId, input.name(), null) != null) {
                throw new BusinessException(CatalogErrorCode.CATEGORY_DUPLICATE);
            }
            try {
                mapper.insertCategory(merchantId, input.name(), input.sort());
            } catch (DuplicateKeyException ignored) {
                throw new BusinessException(CatalogErrorCode.CATEGORY_DUPLICATE);
            }
            return MerchantCategoryView.from(mapper.categoryByName(merchantId, input.name()));
        });
    }

    public MerchantCategoryView editCategory(AuthPrincipal principal, BigInteger id,
                                             CatalogRequests.CategoryEdit input) {
        return writes.execute(tx -> {
            BigInteger merchantId = requireMerchant(principal);
            if (mapper.lockCategory(id, merchantId) == null) {
                throw new BusinessException(CatalogErrorCode.CATEGORY_NOT_FOUND);
            }
            if (mapper.otherCategoryId(merchantId, input.name(), id) != null) {
                throw new BusinessException(CatalogErrorCode.CATEGORY_DUPLICATE);
            }
            try {
                requireOne(mapper.updateCategory(id, merchantId, input.name(), input.sort()));
            } catch (DuplicateKeyException ignored) {
                throw new BusinessException(CatalogErrorCode.CATEGORY_DUPLICATE);
            }
            return MerchantCategoryView.from(mapper.category(id, merchantId));
        });
    }

    public DeletionView deleteCategory(AuthPrincipal principal, BigInteger id) {
        return writes.execute(tx -> {
            BigInteger merchantId = requireMerchant(principal);
            if (mapper.lockCategory(id, merchantId) == null) {
                throw new BusinessException(CatalogErrorCode.CATEGORY_NOT_FOUND);
            }
            if (!mapper.lockCategoryProducts(merchantId, id, false).isEmpty()) {
                throw new BusinessException(CatalogErrorCode.CATEGORY_NOT_EMPTY);
            }
            try {
                requireOne(mapper.deleteCategory(id, merchantId));
            } catch (DataIntegrityViolationException notEmpty) {
                throw new BusinessException(CatalogErrorCode.CATEGORY_NOT_EMPTY);
            }
            return DeletionView.of(id);
        });
    }

    public MerchantCategoryView categoryStatus(AuthPrincipal principal, BigInteger id, int status) {
        return writes.execute(tx -> {
            BigInteger merchantId = requireMerchant(principal);
            if (mapper.lockCategory(id, merchantId) == null) {
                throw new BusinessException(CatalogErrorCode.CATEGORY_NOT_FOUND);
            }
            var current = mapper.category(id, merchantId);
            if (current.status() == status) { return MerchantCategoryView.from(current); }
            // BR-10: disabling requires every product of the category to be off the shelf first.
            if (status == 0 && !mapper.lockCategoryProducts(merchantId, id, true).isEmpty()) {
                throw new BusinessException(CatalogErrorCode.CATEGORY_HAS_LISTED);
            }
            requireOne(mapper.updateCategoryStatus(id, merchantId, status));
            return MerchantCategoryView.from(mapper.category(id, merchantId));
        });
    }

    private MerchantProductView requireProduct(BigInteger merchantId, BigInteger id) {
        var row = mapper.productForMerchant(id, merchantId);
        if (row == null) { throw new BusinessException(CatalogErrorCode.PRODUCT_NOT_FOUND); }
        return MerchantProductView.from(row);
    }

    private BigInteger boundMerchant(AuthPrincipal principal) {
        if (principal == null || !"MERCHANT".equals(principal.role())) {
            throw new AuthFailure(AuthErrorCode.FORBIDDEN);
        }
        BigInteger merchantId = mapper.merchantIdOfUser(principal.userId());
        if (merchantId == null) { throw new AuthFailure(AuthErrorCode.FORBIDDEN); }
        return merchantId;
    }

    private BigInteger requireMerchant(AuthPrincipal principal) {
        if (principal == null) { throw new AuthFailure(AuthErrorCode.UNAUTHENTICATED); }
        var current = mapper.lockUser(principal.userId());
        if (current == null) { throw new AuthFailure(AuthErrorCode.UNAUTHENTICATED); }
        if (current.status() != 1) { throw new AuthFailure(AuthErrorCode.DISABLED); }
        if (current.tokenVersion() != principal.tokenVersion()) {
            throw new AuthFailure(AuthErrorCode.UNAUTHENTICATED);
        }
        if (!"MERCHANT".equals(current.role())) { throw new AuthFailure(AuthErrorCode.FORBIDDEN); }
        BigInteger merchantId = mapper.merchantIdOfUser(current.id());
        if (merchantId == null) { throw new AuthFailure(AuthErrorCode.FORBIDDEN); }
        return merchantId;
    }

    private static void requireOne(int count) {
        if (count != 1) { throw new BusinessException(CommonErrorCode.INTERNAL_ERROR); }
    }
}
