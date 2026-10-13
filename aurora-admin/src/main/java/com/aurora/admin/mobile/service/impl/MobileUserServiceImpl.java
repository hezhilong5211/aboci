package com.aurora.admin.mobile.service.impl;

import com.aurora.admin.mobile.domain.MobileUser;
import com.aurora.admin.mobile.mapper.MobileUserMapper;
import com.aurora.admin.mobile.service.IMobileUserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 移动端用户 Service 实现类
 */
@Service
public class MobileUserServiceImpl extends ServiceImpl<MobileUserMapper, MobileUser>
        implements IMobileUserService {
}
