package com.aurora.admin.mobile.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import cn.binarywang.wx.miniapp.bean.WxMaPhoneNumberInfo;
import com.aurora.admin.mobile.domain.MobileUser;
import com.aurora.admin.mobile.dto.UpdateUserDTO;
import com.aurora.admin.mobile.service.IBeanAccountService;
import com.aurora.admin.mobile.service.IMobileLoginService;
import com.aurora.admin.mobile.service.IMobileUserService;
import com.aurora.admin.mobile.service.WxServiceFactory;
import com.aurora.admin.mobile.util.UserNoGenerator;
import com.aurora.admin.mobile.vo.LoginVO;
import com.aurora.admin.mobile.vo.UserInfoVO;
import com.aurora.common.core.exception.ServiceException;
import com.aurora.common.core.service.ISysConfigService;
import com.aurora.framework.mobile.MobileTokenService;
import com.aurora.framework.mobile.TokenPair;
import com.aurora.framework.utils.EncryptUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Random;

/**
 * 移动端微信登录与用户信息 Service 实现
 * 对齐 WECHAT_JAVA_INTEGRATION.md 第4/5节:getSessionInfo 换 openid、getPhoneNumber 换手机号,
 * 按 openid→unionid→phone→新建 匹配用户,签发双令牌;WxErrorException 统一转 ServiceException。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MobileLoginServiceImpl implements IMobileLoginService {

    private final WxServiceFactory wxServiceFactory;
    private final IMobileUserService mobileUserService;
    private final MobileTokenService mobileTokenService;
    private final IBeanAccountService beanAccountService;
    private final ISysConfigService sysConfigService;
    private final EncryptUtils encryptUtils;

    /** 来源:小程序 */
    private static final String SOURCE_MINI_APP = "1";

    /** 用户状态:停用 */
    private static final String STATUS_DISABLED = "1";

    /** 新用户赠送豆数配置键 */
    private static final String CONFIG_NEW_USER_GIFT = "mobile.bean.new_user_gift";

    @Override
    public LoginVO wxPhoneLogin(String loginCode, String phoneCode) {
        WxMaService wxMaService = wxServiceFactory.getWxMaService();

        // 1. loginCode 换 openid/unionid
        WxMaJscode2SessionResult session;
        try {
            session = wxMaService.getUserService().getSessionInfo(loginCode);
        } catch (WxErrorException e) {
            log.warn("微信登录换 session 失败：{}", e.getMessage());
            throw new ServiceException("微信登录失败，请重试");
        }
        String openid = session.getOpenid();
        String unionid = session.getUnionid();
        if (openid == null || openid.isEmpty()) {
            throw new ServiceException("微信登录失败，获取 openid 失败");
        }

        // 2. phoneCode 换手机号(新版接口,不依赖 session_key)
        String phone;
        try {
            WxMaPhoneNumberInfo phoneInfo = wxMaService.getUserService().getPhoneNumber(phoneCode);
            phone = phoneInfo.getPurePhoneNumber();
        } catch (WxErrorException e) {
            log.warn("获取微信手机号失败：{}", e.getMessage());
            throw new ServiceException("获取手机号失败，请重试");
        }

        // 3. 用户匹配优先级:openid → unionid → phone → 新建
        boolean isNewUser = false;
        MobileUser user = findByOpenid(openid);
        if (user != null) {
            boolean changed = false;
            if (isBlank(user.getPhone()) && !isBlank(phone)) {
                user.setPhone(phone);
                changed = true;
            }
            if (!isBlank(unionid) && isBlank(user.getWxUnionid())) {
                user.setWxUnionid(unionid);
                changed = true;
            }
            if (changed) {
                mobileUserService.updateById(user);
            }
        } else {
            // 3.1 可能此前用手机号注册过 → 补绑微信信息
            MobileUser byPhone = findByPhone(phone);
            if (byPhone != null) {
                byPhone.setWxOpenid(openid);
                if (!isBlank(unionid)) {
                    byPhone.setWxUnionid(unionid);
                }
                mobileUserService.updateById(byPhone);
                user = byPhone;
            } else {
                // 3.2 全新用户
                user = new MobileUser();
                user.setWxOpenid(openid);
                user.setWxUnionid(unionid);
                user.setPhone(phone);
                user.setSource(SOURCE_MINI_APP);
                user.setUserNo(generateUserNo());
                user.setNickName("小光" + randomAlnum4());
                mobileUserService.save(user);
                isNewUser = true;
                grantNewUserGift(user.getUserId());
            }
        }

        // 4. 状态校验
        ensureEnabled(user);

        // 5. 保存本次 code2Session 对应 session_key(AES 密文),供虚拟支付用户态签名。
        persistWxSession(user, session.getSessionKey());

        // 6. 签发双令牌
        TokenPair tokens = mobileTokenService.issue(user.getUserId(), tokenUserName(user));
        return LoginVO.of(tokens, isNewUser, user);
    }

    @Override
    public LoginVO wxLogin(String code) {
        WxMaService wxMaService = wxServiceFactory.getWxMaService();

        WxMaJscode2SessionResult session;
        try {
            session = wxMaService.getUserService().getSessionInfo(code);
        } catch (WxErrorException e) {
            log.warn("微信登录换 session 失败：{}", e.getMessage());
            throw new ServiceException("微信登录失败，请重试");
        }
        String openid = session.getOpenid();
        String unionid = session.getUnionid();
        if (openid == null || openid.isEmpty()) {
            throw new ServiceException("微信登录失败，获取 openid 失败");
        }

        boolean isNewUser = false;
        MobileUser user = findByOpenid(openid);
        // openid 未命中时,若有 unionid 再按 unionid 命中并补绑 openid
        if (user == null && !isBlank(unionid)) {
            user = findByUnionid(unionid);
            if (user != null) {
                user.setWxOpenid(openid);
                mobileUserService.updateById(user);
            }
        }
        if (user == null) {
            user = new MobileUser();
            user.setWxOpenid(openid);
            user.setWxUnionid(unionid);
            user.setSource(SOURCE_MINI_APP);
            user.setUserNo(generateUserNo());
            user.setNickName("小光" + randomAlnum4());
            mobileUserService.save(user);
            isNewUser = true;
            grantNewUserGift(user.getUserId());
        }

        ensureEnabled(user);

        // 保存本次 code2Session 对应 session_key(AES 密文),供虚拟支付用户态签名。
        persistWxSession(user, session.getSessionKey());

        TokenPair tokens = mobileTokenService.issue(user.getUserId(), tokenUserName(user));
        return LoginVO.of(tokens, isNewUser, user);
    }

    @Override
    public void refreshWxSession(Long userId, String code) {
        if (userId == null) {
            throw new ServiceException("用户不能为空");
        }
        if (isBlank(code)) {
            throw new ServiceException("微信登录凭证不能为空");
        }

        MobileUser user = mobileUserService.getById(userId);
        if (user == null) {
            throw new ServiceException("用户不存在");
        }
        ensureEnabled(user);
        if (isBlank(user.getWxOpenid())) {
            throw new ServiceException("用户未绑定微信");
        }

        WxMaJscode2SessionResult session;
        try {
            session = wxServiceFactory.getWxMaService().getUserService().getSessionInfo(code);
        } catch (WxErrorException e) {
            log.warn("刷新微信 session 失败：{}", e.getMessage());
            throw new ServiceException("刷新微信登录状态失败，请重试");
        }
        if (isBlank(session.getOpenid()) || !user.getWxOpenid().equals(session.getOpenid())) {
            throw new ServiceException("微信身份与当前用户不一致");
        }
        persistWxSession(user, session.getSessionKey());
    }

    @Override
    public LoginVO wxBindPhone(Long userId, String phoneCode) {
        MobileUser user = mobileUserService.getById(userId);
        if (user == null) {
            throw new ServiceException("用户不存在");
        }
        WxMaService wxMaService = wxServiceFactory.getWxMaService();

        String phone;
        try {
            phone = wxMaService.getUserService().getPhoneNumber(phoneCode).getPurePhoneNumber();
        } catch (WxErrorException e) {
            log.warn("获取微信手机号失败：{}", e.getMessage());
            throw new ServiceException("获取手机号失败，请重试");
        }

        user.setPhone(phone);
        mobileUserService.updateById(user);

        ensureEnabled(user);

        TokenPair tokens = mobileTokenService.issue(user.getUserId(), tokenUserName(user));
        return LoginVO.of(tokens, false, user);
    }

    @Override
    public UserInfoVO updateUserInfo(Long userId, UpdateUserDTO dto) {
        MobileUser user = mobileUserService.getById(userId);
        if (user == null) {
            throw new ServiceException("用户不存在");
        }
        // 白名单更新:仅昵称/头像/性别,杜绝越权改其他字段
        if (dto.getNickName() != null) {
            user.setNickName(dto.getNickName());
        }
        if (dto.getAvatarUrl() != null) {
            user.setAvatarUrl(dto.getAvatarUrl());
        }
        if (dto.getGender() != null) {
            user.setGender(dto.getGender());
        }
        mobileUserService.updateById(user);
        return UserInfoVO.from(user);
    }

    @Override
    public UserInfoVO getUserInfo(Long userId) {
        MobileUser user = mobileUserService.getById(userId);
        if (user == null) {
            throw new ServiceException("用户不存在");
        }
        return UserInfoVO.from(user);
    }

    /**
     * 按 openid 查用户
     */
    private MobileUser findByOpenid(String openid) {
        return mobileUserService.lambdaQuery()
                .eq(MobileUser::getWxOpenid, openid)
                .one();
    }

    /**
     * 按 unionid 查用户
     */
    private MobileUser findByUnionid(String unionid) {
        return mobileUserService.lambdaQuery()
                .eq(MobileUser::getWxUnionid, unionid)
                .last("limit 1")
                .one();
    }

    /**
     * 按手机号查用户
     */
    private MobileUser findByPhone(String phone) {
        if (isBlank(phone)) {
            return null;
        }
        return mobileUserService.lambdaQuery()
                .eq(MobileUser::getPhone, phone)
                .last("limit 1")
                .one();
    }

    /**
     * 校验账号未被停用
     */
    private void ensureEnabled(MobileUser user) {
        if (STATUS_DISABLED.equals(user.getStatus())) {
            throw new ServiceException("账号已被禁用，请联系客服");
        }
    }

    /**
     * 令牌中的用户名:有手机号用手机号,否则用 wx_{userId} 兜底
     */
    private String tokenUserName(MobileUser user) {
        return !isBlank(user.getPhone()) ? user.getPhone() : "wx_" + user.getUserId();
    }

    /**
     * 加密并持久化微信 session_key。仅更新密钥及刷新时间,避免覆盖用户其他并发修改。
     */
    private void persistWxSession(MobileUser user, String sessionKey) {
        if (user == null || user.getUserId() == null) {
            throw new ServiceException("用户不存在");
        }
        if (isBlank(sessionKey)) {
            throw new ServiceException("微信登录失败，获取 session_key 失败");
        }
        LocalDateTime now = LocalDateTime.now();
        MobileUser update = new MobileUser();
        update.setUserId(user.getUserId());
        update.setWxSessionKey(encryptUtils.encrypt(sessionKey));
        update.setSessionKeyUpdated(now);
        if (!mobileUserService.updateById(update)) {
            throw new ServiceException("微信登录状态保存失败，请重试");
        }
        // 保持当前内存对象与数据库一致;wxSessionKey 带 @JsonIgnore,不会进入响应。
        user.setWxSessionKey(update.getWxSessionKey());
        user.setSessionKeyUpdated(now);
    }

    /**
     * 生成全局唯一用户编号:撞库谓词查 mobile_user.user_no 是否已存在
     */
    private String generateUserNo() {
        return UserNoGenerator.generate(
                LocalDateTime.now(),
                no -> mobileUserService.lambdaQuery().eq(MobileUser::getUserNo, no).exists());
    }

    /**
     * 生成 4 位随机 [a-zA-Z0-9] 后缀(默认昵称用)
     */
    private String randomAlnum4() {
        String chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 4; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * 字符串为 null 或空判断
     */
    private boolean isBlank(String s) {
        return s == null || s.isEmpty();
    }

    /**
     * 新用户注册赠送豆子:读 sys_config 的 mobile.bean.new_user_gift,>0 才发放(经账户服务建账户并 grant);
     * 配置缺失/非法/≤0 均不发放,且赠送失败不影响登录主流程。
     */
    private void grantNewUserGift(Long userId) {
        long gift = parseGiftAmount(sysConfigService.selectConfigByKey(CONFIG_NEW_USER_GIFT));
        if (gift <= 0) {
            return;
        }
        try {
            beanAccountService.ensureAccount(userId);
            beanAccountService.grant(userId, gift, null, "新用户注册赠送");
        } catch (Exception e) {
            // 赠送失败不阻断登录,仅记录告警
            log.warn("新用户赠送豆子失败,userId={}：{}", userId, e.getMessage());
        }
    }

    /**
     * 解析赠送豆数配置:null/空/非数字 → 0
     */
    private long parseGiftAmount(String value) {
        if (isBlank(value)) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            log.warn("新用户赠送豆数配置非法：{}", value);
            return 0L;
        }
    }
}
