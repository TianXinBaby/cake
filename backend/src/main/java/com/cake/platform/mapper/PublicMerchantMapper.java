package com.cake.platform.mapper;

import java.math.BigInteger;
import java.util.List;
import com.cake.platform.entity.PublicMerchantRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Public reads joined with the bound account so a disabled merchant account hides the shop. */
@Mapper
public interface PublicMerchantMapper {
    String COLUMNS = "m.id,m.name,m.phone,m.address,m.description,m.status";
    String FROM = " FROM cake_merchant m JOIN sys_user u ON u.id=m.user_id"
            + " WHERE m.status=1 AND u.status=1";

    @Select("<script>SELECT COUNT(*)" + FROM
            + "<if test='pattern != null'> AND m.name LIKE #{pattern} ESCAPE '!'</if></script>")
    long count(@Param("pattern") String pattern);

    @Select("<script>SELECT " + COLUMNS + FROM
            + "<if test='pattern != null'> AND m.name LIKE #{pattern} ESCAPE '!'</if>"
            + " ORDER BY m.id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<PublicMerchantRecord> page(@Param("pattern") String pattern, @Param("size") int size,
                                    @Param("offset") long offset);

    @Select("SELECT " + COLUMNS + FROM + " AND m.id=#{id,jdbcType=DECIMAL}")
    PublicMerchantRecord byId(@Param("id") BigInteger id);
}
