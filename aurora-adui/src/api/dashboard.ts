import request from '@/utils/request'

// ==================== 首页运营看板 API ====================

/** 近30天趋势项(按天) */
export interface TrendItem {
  // 日期 "MM-dd"
  date: string
  // 当天 AI 产出数
  genCount: number
  // 当天收入(元)
  income: number
}

/** 豆子经济 */
export interface BeanEconomy {
  // 累计充值豆
  recharge: number
  // 累计消耗豆(绝对值)
  consume: number
  // 累计赠送+发放豆
  grant: number
}

/** 模型产出占比项 */
export interface ModelDistItem {
  // 模型名
  name: string
  count: number
}

/** 产出类型占比项 */
export interface TypeDistItem {
  // 产出类型(如 text2img/img2img)
  type: string
  count: number
}

/** 最近订单项 */
export interface RecentOrderItem {
  orderNo: string
  productName: string
  // 金额(元)
  amount: number
  // 状态中文
  statusText: string
  createTime: string
}

/** 最近产出项 */
export interface RecentGenItem {
  userId: number
  taskType: string
  // 状态中文
  statusText: string
  // 消耗豆
  beanCost: number
  createTime: string
}

/** 首页看板聚合数据 */
export interface DashboardVO {
  // 用户总数
  userTotal: number
  // 今日新增用户
  userToday: number
  // 累计 AI 产出
  genTotal: number
  // 今日产出
  genToday: number
  // 昨日产出(用于算环比)
  genYesterday: number
  // 今日活跃创作用户(今日有产出的去重用户数)
  activeCreatorsToday: number
  // 累计收入(元)
  incomeTotal: number
  // 今日收入(元)
  incomeToday: number
  // 有效会员数
  memberActive: number
  // 近30天按天趋势(缺失天补0)
  trend: TrendItem[]
  // 豆子经济
  bean: BeanEconomy
  // 各模型产出占比
  modelDist: ModelDistItem[]
  // 各产出类型占比
  typeDist: TypeDistItem[]
  // 最近5笔订单
  recentOrders: RecentOrderItem[]
  // 最近5条产出
  recentGens: RecentGenItem[]
}

// 获取首页运营看板聚合数据(首页所有登录用户可看)
// 显式标注 Promise<DashboardVO>:响应拦截器已拆包返回业务 data,
// 否则 TS 按 AxiosResponse 推断,调用方访问字段会报"属性不存在"
export function getDashboardOverview(): Promise<DashboardVO> {
  return request({
    url: '/system/dashboard/overview',
    method: 'get'
  })
}
