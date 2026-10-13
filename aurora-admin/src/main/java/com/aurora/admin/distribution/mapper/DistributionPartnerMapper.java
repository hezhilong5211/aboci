package com.aurora.admin.distribution.mapper;

import com.aurora.admin.distribution.domain.DistributionPartner;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DistributionPartnerMapper extends BaseMapper<DistributionPartner> {
    @Select("SELECT * FROM distribution_partner WHERE user_id = #{userId} AND del_flag = '0' LIMIT 1")
    DistributionPartner selectByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM distribution_partner WHERE invite_code = #{inviteCode} AND del_flag = '0' LIMIT 1")
    DistributionPartner selectByInviteCode(@Param("inviteCode") String inviteCode);

    @Select("SELECT COUNT(*) FROM distribution_partner WHERE partner_no = #{partnerNo}")
    long countByPartnerNo(@Param("partnerNo") String partnerNo);

    @Select("SELECT COUNT(*) FROM distribution_partner WHERE invite_code = #{inviteCode}")
    long countByInviteCode(@Param("inviteCode") String inviteCode);

    @Select("SELECT * FROM distribution_partner WHERE partner_id = #{partnerId} AND del_flag = '0' FOR UPDATE")
    DistributionPartner selectByIdForUpdate(@Param("partnerId") Long partnerId);
}
