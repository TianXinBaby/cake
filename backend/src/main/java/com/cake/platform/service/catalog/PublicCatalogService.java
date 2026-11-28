package com.cake.platform.service.catalog;

import java.math.BigInteger;
import java.util.List;
import com.cake.platform.dto.CatalogRequests;
import com.cake.platform.exception.BusinessException;
import com.cake.platform.exception.CatalogErrorCode;
import com.cake.platform.mapper.CatalogMapper;
import com.cake.platform.vo.CategoryView;
import com.cake.platform.vo.PageView;
import com.cake.platform.vo.ProductView;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Anonymous catalogue reads (API-02, 15, 16). Only items of an enabled shop, enabled category and
 * listed product are visible; sold-out products stay visible because stock is not a visibility filter.
 */
@Service
@Profile({"dev", "prod", "mysql-test"})
public class PublicCatalogService {
    private final CatalogMapper mapper;
    private final TransactionTemplate reads;

    public PublicCatalogService(CatalogMapper mapper, PlatformTransactionManager manager) {
        this.mapper = mapper;
        reads = new TransactionTemplate(manager);
        reads.setReadOnly(true);
    }

    public PageView<ProductView> products(CatalogRequests.PublicQuery query) {
        return reads.execute(status -> {
            requireAvailable(query.merchantId());
            List<ProductView> records = query.offset() == null ? List.of()
                    : mapper.publicProducts(query, query.offset()).stream().map(ProductView::from).toList();
            return new PageView<>(records, mapper.publicProductCount(query), query.page(), query.size());
        });
    }

    public ProductView product(BigInteger id) {
        return reads.execute(status -> {
            var row = mapper.product(id);
            if (row == null) { throw new BusinessException(CatalogErrorCode.PRODUCT_NOT_FOUND); }
            requireAvailable(row.merchantId());
            Integer category = mapper.categoryStatus(row.categoryId());
            if (row.status() != 1 || category == null || category != 1) {
                throw new BusinessException(CatalogErrorCode.PRODUCT_NOT_FOUND);
            }
            return ProductView.from(row);
        });
    }

    public List<CategoryView> categories(BigInteger merchantId) {
        return reads.execute(status -> {
            requireAvailable(merchantId);
            return mapper.publicCategories(merchantId).stream().map(CategoryView::from).toList();
        });
    }

    private void requireAvailable(BigInteger merchantId) {
        Integer status = mapper.merchantStatus(merchantId);
        if (status == null || status != 1) { throw new BusinessException(CatalogErrorCode.MERCHANT_UNAVAILABLE); }
    }
}
