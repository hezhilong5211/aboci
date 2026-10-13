/**
 * 会员管理 API 接口定义(会员等级配置 + 手动开通 + 会员列表)
 * url 镜像后端路径,不带 /api 前缀(request 的 baseURL 已为 /api)
 *
 * @author Aurora
 */
import request from '@/utils/request';

/**
 * 会员等级配置
 */
export interface MembershipLevel {
  levelId?: number;
  name?: string;
  // 每周期送豆数
  grantBeans?: number;
  // 价格(单位:元)
  price?: number;
  // 微信虚拟支付道具 ID(productId)
  virtualProductId?: string;
  // 会员时长(天),购买后有效天数
  durationDays?: number;
  sort?: number;
  // 卡片背景图(个人中心会员卡背景,后端裸实体 card_bg)
  cardBg?: string;
  // 状态(0启用/1停用)
  status?: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * 会员(已开通)列表行:展示当前等级快照
 */
export interface MembershipUser {
  id?: number;
  userId?: number;
  userNo?: string;
  nickName?: string;
  phone?: string;
  // 当前等级名(快照)
  levelName?: string;
  // 到期时间
  expireTime?: string;
}

// ==========================================
// 会员等级配置 CRUD
// ==========================================

/**
 * 查询会员等级列表(按 sort)
 */
export function listMembershipLevel() {
  return request({
    url: '/system/membership/level/list',
    method: 'get'
  });
}

/**
 * 新增会员等级
 */
export function addMembershipLevel(data: MembershipLevel) {
  return request({
    url: '/system/membership/level',
    method: 'post',
    data
  });
}

/**
 * 修改会员等级
 */
export function updateMembershipLevel(data: MembershipLevel) {
  return request({
    url: '/system/membership/level',
    method: 'put',
    data
  });
}

/**
 * 删除会员等级(后端逻辑删,不影响已开通用户快照)
 */
export function removeMembershipLevel(levelId: number) {
  return request({
    url: '/system/membership/level/' + levelId,
    method: 'delete'
  });
}

// ==========================================
// 手动开通会员 + 会员列表
// ==========================================

/**
 * 后台手动开通会员(叠加制:新建一段会员 + 发订阅豆)
 */
export function grantMembership(data: { userId: number; levelId: number }) {
  return request({
    url: '/system/membership/grant',
    method: 'post',
    data
  });
}

/**
 * 会员列表查询参数
 */
export interface MembershipUserQuery {
  pageNum: number;
  pageSize: number;
  // 关键词:后端对 昵称/手机号 做模糊匹配
  keyword?: string;
  // 等级筛选
  levelId?: number;
}

/**
 * 分页查询已开通会员列表(只列当前有效会员)
 */
export function listMembershipUser(query: MembershipUserQuery) {
  return request({
    url: '/system/membership/user/list',
    method: 'get',
    params: query
  });
}
