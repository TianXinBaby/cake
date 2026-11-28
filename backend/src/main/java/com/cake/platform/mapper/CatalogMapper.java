package com.cake.platform.mapper;

import java.math.BigInteger;
import java.util.List;
import com.cake.platform.dto.CatalogRequests;
import com.cake.platform.entity.AccountState;
import com.cake.platform.entity.CategoryRecord;
import com.cake.platform.entity.ProductRecord;
import com.cake.platform.entity.StockRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Store-scoped product and category access. Every merchant statement carries merchant_id derived from
 * the authenticated binding; public statements additionally require an enabled shop and category.
 */
@Mapper
public interface CatalogMapper {
    String CATEGORY_COLUMNS = "c.id,c.merchant_id,c.name,c.sort,c.status,c.create_time,c.update_time";
    String PRODUCT_COLUMNS = "p.id,p.merchant_id,m.name AS merchant_name,p.category_id,c.name AS category_name,"
            + "p.name,p.spec,p.price,p.stock,p.image,p.description,p.status,p.create_time,p.update_time";
    String PRODUCT_JOIN = " FROM cake_product p JOIN cake_merchant m ON m.id=p.merchant_id"
            + " JOIN cake_category c ON c.id=p.category_id AND c.merchant_id=p.merchant_id ";
    String PUBLIC_FILTER = " WHERE p.merchant_id=#{q.merchantId,jdbcType=DECIMAL}"
            + " AND m.status=1 AND c.status=1 AND p.status=1";
    String MERCHANT_FILTER = " WHERE p.merchant_id=#{merchantId,jdbcType=DECIMAL}";

    @Select("SELECT status FROM cake_merchant WHERE id=#{id,jdbcType=DECIMAL}")
    Integer merchantStatus(@Param("id") BigInteger id);

    @Select("SELECT id,role,status,token_version FROM sys_user WHERE id=#{id,jdbcType=DECIMAL} FOR UPDATE")
    AccountState lockUser(@Param("id") BigInteger id);

    @Select("SELECT id FROM cake_merchant WHERE user_id=#{userId,jdbcType=DECIMAL}")
    BigInteger merchantIdOfUser(@Param("userId") BigInteger userId);

    @Select("SELECT " + CATEGORY_COLUMNS + " FROM cake_category c"
            + " WHERE c.merchant_id=#{merchantId,jdbcType=DECIMAL} AND c.status=1 ORDER BY c.sort,c.id")
    List<CategoryRecord> publicCategories(@Param("merchantId") BigInteger merchantId);

    @Select("SELECT status FROM cake_category WHERE id=#{id,jdbcType=DECIMAL}")
    Integer categoryStatus(@Param("id") BigInteger id);

    @Select("<script>SELECT " + PRODUCT_COLUMNS + PRODUCT_JOIN + PUBLIC_FILTER
            + "<if test='q.categoryId != null'> AND p.category_id=#{q.categoryId,jdbcType=DECIMAL}</if>"
            + "<if test='q.pattern != null'> AND p.name LIKE #{q.pattern} ESCAPE '!'</if>"
            + " ORDER BY p.id DESC LIMIT #{q.size} OFFSET #{offset}</script>")
    List<ProductRecord> publicProducts(@Param("q") CatalogRequests.PublicQuery q, @Param("offset") long offset);

    @Select("<script>SELECT COUNT(*) FROM cake_product p JOIN cake_merchant m ON m.id=p.merchant_id"
            + " JOIN cake_category c ON c.id=p.category_id AND c.merchant_id=p.merchant_id" + PUBLIC_FILTER
            + "<if test='q.categoryId != null'> AND p.category_id=#{q.categoryId,jdbcType=DECIMAL}</if>"
            + "<if test='q.pattern != null'> AND p.name LIKE #{q.pattern} ESCAPE '!'</if></script>")
    long publicProductCount(@Param("q") CatalogRequests.PublicQuery q);

    @Select("SELECT " + PRODUCT_COLUMNS + PRODUCT_JOIN + " WHERE p.id=#{id,jdbcType=DECIMAL}")
    ProductRecord product(@Param("id") BigInteger id);

    @Select("<script>SELECT " + PRODUCT_COLUMNS + PRODUCT_JOIN + MERCHANT_FILTER
            + "<if test='q.categoryId != null'> AND p.category_id=#{q.categoryId,jdbcType=DECIMAL}</if>"
            + "<if test='q.status != null'> AND p.status=#{q.status}</if>"
            + "<if test='q.pattern != null'> AND p.name LIKE #{q.pattern} ESCAPE '!'</if>"
            + " ORDER BY p.id DESC LIMIT #{q.size} OFFSET #{offset}</script>")
    List<ProductRecord> merchantProducts(@Param("q") CatalogRequests.ProductQuery q,
            @Param("merchantId") BigInteger merchantId, @Param("offset") long offset);

    @Select("<script>SELECT COUNT(*) FROM cake_product p JOIN cake_merchant m ON m.id=p.merchant_id"
            + " JOIN cake_category c ON c.id=p.category_id AND c.merchant_id=p.merchant_id" + MERCHANT_FILTER
            + "<if test='q.categoryId != null'> AND p.category_id=#{q.categoryId,jdbcType=DECIMAL}</if>"
            + "<if test='q.status != null'> AND p.status=#{q.status}</if>"
            + "<if test='q.pattern != null'> AND p.name LIKE #{q.pattern} ESCAPE '!'</if></script>")
    long merchantProductCount(@Param("q") CatalogRequests.ProductQuery q,
            @Param("merchantId") BigInteger merchantId);

    @Select("SELECT " + PRODUCT_COLUMNS + PRODUCT_JOIN
            + " WHERE p.id=#{id,jdbcType=DECIMAL} AND p.merchant_id=#{merchantId,jdbcType=DECIMAL}")
    ProductRecord productForMerchant(@Param("id") BigInteger id, @Param("merchantId") BigInteger merchantId);

    @Select("SELECT id FROM cake_product WHERE id=#{id,jdbcType=DECIMAL}"
            + " AND merchant_id=#{merchantId,jdbcType=DECIMAL} FOR UPDATE")
    BigInteger lockProduct(@Param("id") BigInteger id, @Param("merchantId") BigInteger merchantId);

    @Insert("INSERT INTO cake_product(merchant_id,category_id,name,spec,price,stock,image,description,status)"
            + " VALUES(#{merchantId,jdbcType=DECIMAL},#{input.categoryId,jdbcType=DECIMAL},#{input.name},"
            + "#{input.spec},#{input.price},#{input.stock},#{input.image},#{input.description},0)")
    void insertProduct(@Param("merchantId") BigInteger merchantId,
                       @Param("input") CatalogRequests.ProductCreation input);

    /** Same connection and transaction as the preceding insert. */
    @Select("SELECT LAST_INSERT_ID()")
    BigInteger lastInsertId();

    @Update("UPDATE cake_product SET category_id=#{input.categoryId,jdbcType=DECIMAL},name=#{input.name},"
            + "spec=#{input.spec},price=#{input.price},image=#{input.image},description=#{input.description},"
            + "update_time=CURRENT_TIMESTAMP(3) WHERE id=#{id,jdbcType=DECIMAL}"
            + " AND merchant_id=#{merchantId,jdbcType=DECIMAL}")
    int updateProduct(@Param("id") BigInteger id, @Param("merchantId") BigInteger merchantId,
                      @Param("input") CatalogRequests.ProductEdit input);

    @Update("UPDATE cake_product SET status=#{status},update_time=CURRENT_TIMESTAMP(3)"
            + " WHERE id=#{id,jdbcType=DECIMAL} AND merchant_id=#{merchantId,jdbcType=DECIMAL}"
            + " AND status != #{status}")
    int updateProductStatus(@Param("id") BigInteger id, @Param("merchantId") BigInteger merchantId,
                            @Param("status") int status);

    @Update("UPDATE cake_product SET stock=stock+#{delta},update_time=CURRENT_TIMESTAMP(3)"
            + " WHERE id=#{id,jdbcType=DECIMAL} AND merchant_id=#{merchantId,jdbcType=DECIMAL}"
            + " AND stock=#{expectedStock} AND stock+#{delta} BETWEEN 0 AND 2147483647")
    int updateStock(@Param("id") BigInteger id, @Param("merchantId") BigInteger merchantId,
                    @Param("delta") int delta, @Param("expectedStock") int expectedStock);

    @Select("SELECT stock,update_time FROM cake_product WHERE id=#{id,jdbcType=DECIMAL}")
    StockRecord stock(@Param("id") BigInteger id);

    @Update("DELETE FROM cake_product WHERE id=#{id,jdbcType=DECIMAL}"
            + " AND merchant_id=#{merchantId,jdbcType=DECIMAL}")
    int deleteProduct(@Param("id") BigInteger id, @Param("merchantId") BigInteger merchantId);

    @Select("SELECT COUNT(*) FROM cake_order_item WHERE product_id=#{productId,jdbcType=DECIMAL}")
    long productReferenceCount(@Param("productId") BigInteger productId);

    @Select("<script>SELECT " + CATEGORY_COLUMNS + " FROM cake_category c"
            + " WHERE c.merchant_id=#{merchantId,jdbcType=DECIMAL}"
            + "<if test='status != null'> AND c.status=#{status}</if>"
            + " ORDER BY c.sort,c.id</script>")
    List<CategoryRecord> merchantCategories(@Param("merchantId") BigInteger merchantId,
                                            @Param("status") Integer status);

    @Select("SELECT " + CATEGORY_COLUMNS + " FROM cake_category c"
            + " WHERE c.id=#{id,jdbcType=DECIMAL} AND c.merchant_id=#{merchantId,jdbcType=DECIMAL}")
    CategoryRecord category(@Param("id") BigInteger id, @Param("merchantId") BigInteger merchantId);

    @Select("SELECT " + CATEGORY_COLUMNS + " FROM cake_category c"
            + " WHERE c.merchant_id=#{merchantId,jdbcType=DECIMAL} AND c.name=#{name}")
    CategoryRecord categoryByName(@Param("merchantId") BigInteger merchantId, @Param("name") String name);

    @Select("<script>SELECT id FROM cake_category WHERE merchant_id=#{merchantId,jdbcType=DECIMAL}"
            + " AND name=#{name}<if test='excludeId != null'> AND id != #{excludeId,jdbcType=DECIMAL}</if>"
            + " LIMIT 1</script>")
    BigInteger otherCategoryId(@Param("merchantId") BigInteger merchantId, @Param("name") String name,
                               @Param("excludeId") BigInteger excludeId);

    @Select("SELECT id FROM cake_category WHERE id=#{id,jdbcType=DECIMAL}"
            + " AND merchant_id=#{merchantId,jdbcType=DECIMAL} FOR UPDATE")
    BigInteger lockCategory(@Param("id") BigInteger id, @Param("merchantId") BigInteger merchantId);

    @Insert("INSERT INTO cake_category(merchant_id,name,sort,status)"
            + " VALUES(#{merchantId,jdbcType=DECIMAL},#{name},#{sort},1)")
    void insertCategory(@Param("merchantId") BigInteger merchantId, @Param("name") String name,
                        @Param("sort") int sort);

    @Update("UPDATE cake_category SET name=#{name},sort=#{sort},update_time=CURRENT_TIMESTAMP(3)"
            + " WHERE id=#{id,jdbcType=DECIMAL} AND merchant_id=#{merchantId,jdbcType=DECIMAL}")
    int updateCategory(@Param("id") BigInteger id, @Param("merchantId") BigInteger merchantId,
                       @Param("name") String name, @Param("sort") int sort);

    @Update("UPDATE cake_category SET status=#{status},update_time=CURRENT_TIMESTAMP(3)"
            + " WHERE id=#{id,jdbcType=DECIMAL} AND merchant_id=#{merchantId,jdbcType=DECIMAL}"
            + " AND status != #{status}")
    int updateCategoryStatus(@Param("id") BigInteger id, @Param("merchantId") BigInteger merchantId,
                             @Param("status") int status);

    @Update("DELETE FROM cake_category WHERE id=#{id,jdbcType=DECIMAL}"
            + " AND merchant_id=#{merchantId,jdbcType=DECIMAL}")
    int deleteCategory(@Param("id") BigInteger id, @Param("merchantId") BigInteger merchantId);

    @Select("<script>SELECT id FROM cake_product WHERE merchant_id=#{merchantId,jdbcType=DECIMAL}"
            + " AND category_id=#{categoryId,jdbcType=DECIMAL}"
            + "<if test='listedOnly'> AND status=1</if> FOR UPDATE</script>")
    List<BigInteger> lockCategoryProducts(@Param("merchantId") BigInteger merchantId,
                                          @Param("categoryId") BigInteger categoryId,
                                          @Param("listedOnly") boolean listedOnly);
}
