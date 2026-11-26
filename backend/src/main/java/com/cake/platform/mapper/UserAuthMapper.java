package com.cake.platform.mapper;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import com.cake.platform.entity.UserAccount;
import org.apache.ibatis.annotations.*;

public interface UserAuthMapper {
    String COLUMNS = "id, username, password, nickname, phone, role, status, token_version, "
            + "login_fail_count, locked_until, create_time, update_time";

    @Select("SELECT " + COLUMNS + " FROM sys_user WHERE username=#{username} FOR UPDATE")
    UserAccount lockByUsername(String username);
    @Select("SELECT " + COLUMNS + " FROM sys_user WHERE id=#{id} FOR UPDATE")
    UserAccount lockById(BigInteger id);
    @Select("SELECT " + COLUMNS + " FROM sys_user WHERE id=#{id}")
    UserAccount findById(BigInteger id);
    @Select("SELECT id FROM cake_merchant WHERE user_id=#{userId}")
    List<BigInteger> merchantIds(BigInteger userId);
    @Select("SELECT CURRENT_TIMESTAMP(3)")
    LocalDateTime now();

    @Insert("INSERT INTO sys_user(username,password,nickname,phone,role,status,token_version,login_fail_count,locked_until) "
            + "VALUES(#{phone},#{hash},#{nickname},#{phone},'CUSTOMER',1,0,0,NULL)")
    void register(@Param("phone") String phone, @Param("nickname") String nickname, @Param("hash") String hash);
    @Select("SELECT " + COLUMNS + " FROM sys_user WHERE username=#{username}")
    UserAccount findByUsername(String username);
    @Update("UPDATE sys_user SET login_fail_count=0,locked_until=NULL,update_time=CURRENT_TIMESTAMP(3) WHERE id=#{id}")
    void resetLogin(BigInteger id);
    @Update("UPDATE sys_user SET login_fail_count=#{count},locked_until=#{until},update_time=CURRENT_TIMESTAMP(3) WHERE id=#{id}")
    void failedLogin(@Param("id") BigInteger id, @Param("count") int count, @Param("until") LocalDateTime until);
    @Update("UPDATE sys_user SET token_version=token_version+1,update_time=CURRENT_TIMESTAMP(3) WHERE id=#{id}")
    void logout(BigInteger id);
    @Update("UPDATE sys_user SET nickname=#{nickname},update_time=CURRENT_TIMESTAMP(3) WHERE id=#{id}")
    void nickname(@Param("id") BigInteger id, @Param("nickname") String nickname);
    @Update("UPDATE sys_user SET password=#{hash},token_version=token_version+1,update_time=CURRENT_TIMESTAMP(3) WHERE id=#{id}")
    void password(@Param("id") BigInteger id, @Param("hash") String hash);
}
