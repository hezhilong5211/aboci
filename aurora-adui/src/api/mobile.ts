/**
 * 移动端管理 API 接口定义(移动端用户 + 微信应用配置)
 * url 镜像后端路径,不带 /api 前缀(request 的 baseURL 已为 /api)
 *
 * @author Aurora
 */
import request from '@/utils/request';

/**
 * 移动端用户
 */
export interface MobileUser {
  userId?: number;
  // 用户编号(Ts + yyMMddHH + 4位随机字母数字 + 4位随机数字)
  userNo?: string;
  wxOpenid?: string;
  wxUnionid?: string;
  phone?: string;
  nickName?: string;
  avatarUrl?: string;
  // 性别(0未知/1男/2女)
  gender?: number;
  source?: string;
  // 当前会员等级名(后端关联 user_membership 快照返回,非会员为空)
  membershipLevel?: string;
  // 当前会员等级ID(非会员为空)
  membershipLevelId?: number;
  // 状态(0正常/1停用)
  status?: string;
  // 豆子余额(后端关联 bean_account 返回)
  balance?: number;
  createTime?: string;
  updateTime?: string;
}

/**
 * 微信应用配置
 */
export interface SysWxConfig {
  wxId?: number;
  // 应用类型(mini_app小程序/pay支付)
  appType?: string;
  appName?: string;
  appId?: string;
  // 小程序密钥(出参脱敏,编辑时为 **** 则不更新)
  appSecret?: string;
  mchId?: string;
  // 支付 APIv3 密钥(出参脱敏)
  apiV3Key?: string;
  // 证书私钥内容(出参脱敏)
  privateKey?: string;
  // 证书内容(出参脱敏)
  privateCert?: string;
  // 微信支付公钥ID(PUB_KEY_ID_ 开头;公开内容,不脱敏)
  publicKeyId?: string;
  // 微信支付公钥内容(pub_key.pem;公开内容,不脱敏)
  publicKey?: string;
  notifyUrl?: string;
  // ==== 虚拟支付(appType=virtual_pay)====
  // 虚拟支付商户号 OfferId
  offerId?: string;
  // 沙箱 AppKey(出参脱敏)
  sandboxAppKey?: string;
  // 现网 AppKey(出参脱敏)
  prodAppKey?: string;
  // 虚拟支付环境:0现网/1沙箱
  env?: number;
  // 消息推送 Token(出参脱敏)
  pushToken?: string;
  // 消息推送 EncodingAESKey(出参脱敏)
  pushAesKey?: string;
  // 是否启用(1启用/0停用)
  isActive?: number;
  createTime?: string;
  updateTime?: string;
}

/**
 * 移动端底部导航 Tab(后台只可编辑 名称/普通态 icon/选中态 icon/启用;tab_key、page_path 只读)
 */
export interface SysMobileTabbar {
  tabId?: number;
  // Tab 标识(chat/create/cases/profile),前端固定,只读
  tabKey?: string;
  tabName?: string;
  // 普通态 icon 可访问 URL
  iconNormal?: string;
  // 选中态 icon 可访问 URL
  iconActive?: string;
  sort?: number;
  // 是否启用(1启用/0停用)
  visible?: number;
  // 页面路径占位,只读
  pagePath?: string;
  createTime?: string;
  updateTime?: string;
}

// ==========================================
// 移动端用户
// ==========================================

/**
 * 移动端用户列表查询参数
 */
export interface MobileUserQuery {
  pageNum: number;
  pageSize: number;
  // 关键词:后端对 昵称/手机号/用户编号 做 OR 模糊匹配
  keyword?: string;
}

/**
 * 分页查询移动端用户
 */
export function listMobileUser(query: MobileUserQuery) {
  return request({
    url: '/system/mobile/user/list',
    method: 'get',
    params: query
  });
}

/**
 * 启用/停用移动端用户(status:0正常/1停用)
 */
export function editMobileUserStatus(id: number, status: string) {
  return request({
    url: '/system/mobile/user/' + id,
    method: 'put',
    data: { status }
  });
}

export interface MembershipLevelOption {
  levelId: number;
  name: string;
}

/** 查询移动端用户列表可选的已启用会员等级。 */
export function listMobileUserMembershipLevelOptions() {
  return request({
    url: '/system/mobile/user/membership-level-options',
    method: 'get'
  });
}

/** 调整当前会员等级；levelId 为空表示无会员。 */
export function editMobileUserMembership(id: number, levelId: number | null) {
  return request({
    url: `/system/mobile/user/${id}/membership`,
    method: 'put',
    data: { levelId }
  });
}

// ==========================================
// 豆子账户(后台手动增减豆 + 流水查看)
// ==========================================

/**
 * 豆子流水
 */
export interface BeanTransaction {
  id?: number;
  userId?: number;
  // 类型(GIFT赠送/ISSUE发放/RECHARGE充值/DEDUCT扣费/REFUND退还/ADJUST后台调整)
  type?: string;
  // 变动值(正负)
  changeValue?: number;
  // 余额快照
  balanceAfter?: number;
  // 关联业务单号(可空)
  bizNo?: string;
  remark?: string;
  createTime?: string;
}

/**
 * 后台手动增减豆(amount 可正可负,记流水)
 */
export function adjustBean(data: { userId: number; amount: number; remark?: string }) {
  return request({
    url: '/system/mobile/bean/adjust',
    method: 'post',
    data
  });
}

/**
 * 分页查询某用户豆子流水
 */
export function listBeanTransactions(params: { userId: number; pageNum: number; pageSize: number }) {
  return request({
    url: '/system/mobile/bean/transactions',
    method: 'get',
    params
  });
}

// ==========================================
// 微信应用配置
// ==========================================

/**
 * 分页查询微信配置
 */
export function listWxConfig(query: any) {
  return request({
    url: '/system/wx-config/list',
    method: 'get',
    params: query
  });
}

/**
 * 获取微信配置详情
 */
export function getWxConfig(wxId: number) {
  return request({
    url: '/system/wx-config/' + wxId,
    method: 'get'
  });
}

/**
 * 新增微信配置
 */
export function addWxConfig(data: SysWxConfig) {
  return request({
    url: '/system/wx-config',
    method: 'post',
    data: data
  });
}

/**
 * 修改微信配置
 */
export function updateWxConfig(data: SysWxConfig) {
  return request({
    url: '/system/wx-config',
    method: 'put',
    data: data
  });
}

/**
 * 删除微信配置
 */
export function delWxConfig(wxId: number) {
  return request({
    url: '/system/wx-config/' + wxId,
    method: 'delete'
  });
}

// ==========================================
// 移动端底部导航 Tabbar
// ==========================================

/**
 * 查询底部导航 Tab 列表(固定 4 条)
 */
export function listMobileTabbar() {
  return request({
    url: '/system/mobile/tabbar/list',
    method: 'get'
  });
}

/**
 * 修改底部导航 Tab(名称/icon/启用态)
 */
export function updateMobileTabbar(data: SysMobileTabbar) {
  return request({
    url: '/system/mobile/tabbar',
    method: 'put',
    data: data
  });
}
