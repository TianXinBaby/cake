package com.cake.platform.service.merchant;

import java.math.BigInteger;
import java.util.List;
import com.cake.platform.dto.PublicMerchantRequests;
import com.cake.platform.exception.BusinessException;
import com.cake.platform.exception.PublicMerchantErrorCode;
import com.cake.platform.mapper.PublicMerchantMapper;
import com.cake.platform.vo.PageView;
import com.cake.platform.vo.PublicMerchantView;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** API-33/34: anonymous reads of enabled shops whose bound merchant account is also enabled. */
@Service
@Profile({"dev", "prod", "mysql-test"})
public class PublicMerchantService {
    private final PublicMerchantMapper mapper;
    private final TransactionTemplate reads;

    public PublicMerchantService(PublicMerchantMapper mapper, PlatformTransactionManager manager) {
        this.mapper = mapper;
        reads = new TransactionTemplate(manager);
        reads.setReadOnly(true);
    }

    public PageView<PublicMerchantView> merchants(PublicMerchantRequests.ListQuery query) {
        return reads.execute(status -> {
            List<PublicMerchantView> records = query.offset() == null ? List.of()
                    : mapper.page(query.pattern(), query.size(), query.offset()).stream()
                            .map(PublicMerchantView::from).toList();
            return new PageView<>(records, mapper.count(query.pattern()), query.page(), query.size());
        });
    }

    public PublicMerchantView merchant(BigInteger id) {
        return reads.execute(status -> {
            var row = mapper.byId(id);
            if (row == null) { throw new BusinessException(PublicMerchantErrorCode.MERCHANT_NOT_FOUND); }
            return PublicMerchantView.from(row);
        });
    }
}
