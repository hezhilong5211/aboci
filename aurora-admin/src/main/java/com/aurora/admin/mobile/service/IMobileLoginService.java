package com.aurora.admin.mobile.service;

import com.aurora.admin.mobile.dto.UpdateUserDTO;
import com.aurora.admin.mobile.vo.LoginVO;
import com.aurora.admin.mobile.vo.UserInfoVO;

/**
 * 移动端微信登录与用户信息 Service 接口
 */
public interface IMobileLoginService {

    /**
     * 微信手机号一键登录
     * loginCode 换 openid/unionid,phoneCode 换手机号,按 openid→unionid→phone→新建 匹配用户,签发双令牌。
     *
     * @param loginCode 登录凭证
     * @param phoneCode 手机号凭证
     * @return 登录结果(双令牌+是否新用户+用户信息)
     */
    LoginVO wxPhoneLogin(String loginCode, String phoneCode);

    /**
     * 仅 openid 静默登录(不取手机号)
     *
     * @param code 登录凭证
     * @return 登录结果
     */
    LoginVO wxLogin(String code);

    /**
     * 已登录用户刷新微信 session_key,供虚拟支付用户态签名使用。
     * code2Session 返回的 openid 必须与当前用户已绑定 openid 一致。
     *
     * @param userId 当前用户ID
     * @param code   wx.login 获取的一次性登录凭证
     */
    void refreshWxSession(Long userId, String code);

    /**
     * 已登录用户补绑手机号
     *
     * @param userId    当前用户ID
     * @param phoneCode 手机号凭证
     * @return 登录结果(刷新令牌+最新用户信息)
     */
    LoginVO wxBindPhone(Long userId, String phoneCode);

    /**
     * 更新用户信息(白名单:昵称/头像/性别)
     *
     * @param userId 当前用户ID
     * @param dto    待更新字段
     * @return 更新后的用户信息
     */
    UserInfoVO updateUserInfo(Long userId, UpdateUserDTO dto);

    /**
     * 获取当前用户信息
     *
     * @param userId 当前用户ID
     * @return 用户信息
     */
    UserInfoVO getUserInfo(Long userId);
}
