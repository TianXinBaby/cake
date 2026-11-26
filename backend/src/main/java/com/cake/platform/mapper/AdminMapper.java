package com.cake.platform.mapper;

import java.math.BigInteger;
import java.util.List;
import com.cake.platform.dto.AdminRequests;
import com.cake.platform.entity.MerchantRecord;
import com.cake.platform.entity.UserAccount;
import org.apache.ibatis.annotations.*;

/** Parameter-bound platform queries on the two existing tables; no schema or pagination dependency. */
public interface AdminMapper {
    String MERCHANT_COLUMNS = "m.id,m.user_id,u.username,m.name,m.phone,m.address,m.description,m.status,m.create_time,m.update_time";
    String MERCHANT_FILTER = "<where><if test='q.status != null'>m.status=#{q.status}</if>"
            + "<if test='q.pattern != null'> AND m.name LIKE #{q.pattern} ESCAPE '!'</if></where>";
    String CUSTOMER_FILTER = "WHERE role='CUSTOMER' <if test='q.status != null'>AND status=#{q.status}</if>"
            + "<if test='q.pattern != null'> AND (nickname LIKE #{q.pattern} ESCAPE '!' OR phone LIKE #{q.pattern} ESCAPE '!')</if>";

    @Select("<script>SELECT COUNT(*) FROM cake_merchant m " + MERCHANT_FILTER + "</script>")
    long merchantCount(@Param("q") AdminRequests.ListQuery query);
    @Select("<script>SELECT " + MERCHANT_COLUMNS + " FROM cake_merchant m JOIN sys_user u ON u.id=m.user_id "
            + MERCHANT_FILTER + " ORDER BY m.id DESC LIMIT #{q.size} OFFSET #{offset}</script>")
    List<MerchantRecord> merchants(@Param("q") AdminRequests.ListQuery query, @Param("offset") long offset);
    @Select("SELECT " + MERCHANT_COLUMNS + " FROM cake_merchant m JOIN sys_user u ON u.id=m.user_id WHERE m.id=#{id,jdbcType=DECIMAL}")
    MerchantRecord merchant(BigInteger id);
    @Select("SELECT " + MERCHANT_COLUMNS + " FROM cake_merchant m JOIN sys_user u ON u.id=m.user_id WHERE m.user_id=#{userId,jdbcType=DECIMAL}")
    MerchantRecord merchantByUser(BigInteger userId);
    @Select("SELECT id FROM cake_merchant WHERE id=#{id,jdbcType=DECIMAL} FOR UPDATE")
    BigInteger lockMerchant(BigInteger id);

    @Insert("INSERT INTO sys_user(username,password,nickname,phone,role,status,token_version,login_fail_count,locked_until) "
            + "VALUES(#{input.accountPhone},#{hash},#{input.nickname},#{input.accountPhone},'MERCHANT',1,0,0,NULL)")
    void createUser(@Param("input") AdminRequests.Creation input, @Param("hash") String hash);
    @Insert("INSERT INTO cake_merchant(user_id,name,phone,address,description,status) "
            + "VALUES(#{userId,jdbcType=DECIMAL},#{input.name},#{input.phone},#{input.address},#{input.description},1)")
    void createMerchant(@Param("userId") BigInteger userId, @Param("input") AdminRequests.Creation input);
    @Update("UPDATE cake_merchant SET name=#{input.name},phone=#{input.phone},address=#{input.address},"
            + "description=#{input.description},update_time=CURRENT_TIMESTAMP(3) WHERE id=#{id,jdbcType=DECIMAL}")
    int editMerchant(@Param("id") BigInteger id, @Param("input") AdminRequests.Edit input);
    @Update("UPDATE cake_merchant SET status=#{status},update_time=CURRENT_TIMESTAMP(3) WHERE id=#{id,jdbcType=DECIMAL} AND status != #{status}")
    int merchantStatus(@Param("id") BigInteger id, @Param("status") int status);

    @Select("<script>SELECT COUNT(*) FROM sys_user " + CUSTOMER_FILTER + "</script>")
    long customerCount(@Param("q") AdminRequests.ListQuery query);
    @Select("<script>SELECT " + UserAuthMapper.COLUMNS + " FROM sys_user " + CUSTOMER_FILTER
            + " ORDER BY id DESC LIMIT #{q.size} OFFSET #{offset}</script>")
    List<UserAccount> customers(@Param("q") AdminRequests.ListQuery query, @Param("offset") long offset);
    @Select("SELECT " + UserAuthMapper.COLUMNS + " FROM sys_user WHERE id=#{id,jdbcType=DECIMAL} FOR UPDATE")
    UserAccount lockUser(BigInteger id);
    @Update("UPDATE sys_user SET status=#{status},token_version=token_version+1,update_time=CURRENT_TIMESTAMP(3) "
            + "WHERE id=#{id,jdbcType=DECIMAL} AND role='CUSTOMER' AND status != #{status}")
    int customerStatus(@Param("id") BigInteger id, @Param("status") int status);
}
