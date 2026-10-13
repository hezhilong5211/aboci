/**
 * 支付订单 API 接口定义(后台只读:列表 + 详情)
 * url 镜像后端路径,不带 /api 前缀(request 的 baseURL 已为 /api)
 *
 * @author Aurora
 */
import request from '@/utils/request';

/**
 * 支付订单对象(后端 PayOrder)
 * amount 为分(Integer),展示时 ÷100 转元
 */
export interface PayOrder {
  // 主键 ID(详情接口按此查询)
  id?: number;
  // 订单号
  orderNo?: string;
  // 用户 ID
  userId?: number;
  // 微信 openId
  openId?: string;
  // 金额(分)
  amount?: number;
  // 订单标题
  subject?: string;
  // 商品类型:MEMBERSHIP 会员 / BEAN_RECHARGE 豆子充值 / 空
  productType?: string;
  // 商品名称
  productName?: string;
  // 支付渠道:WXPAY 传统微信支付 / VIRTUAL 微信虚拟支付
  payChannel?: string;
  // 微信虚拟支付道具 ID 快照
  virtualProductId?: string;
  // 微信虚拟支付订单号
  wxOrderId?: string;
  // 虚拟支付环境:0 现网 / 1 沙箱
  env?: number;
  // 权益发放状态
  fulfillmentStatus?: number;
  // 最近一次权益发放失败原因
  fulfillmentError?: string;
  // 权益发放完成时间
  fulfilledAt?: string;
  // 微信侧发货确认:0 待确认 / 1 已确认
  deliveryConfirmed?: number;
  // 微信侧发货确认时间
  deliveryConfirmedAt?: string;
  // 状态:0 待支付 / 1 已支付 / 2 已退款 / 3 已关闭 / 4 已支付待退款
  status?: number;
  // 第三方交易号
  transactionId?: string;
  // 支付时间
  payTime?: string;
  // 退款单号
  refundNo?: string;
  // 退款金额(分)
  refundAmount?: number;
  // 退款时间
  refundTime?: string;
  // 下单时间
  createTime?: string;
}

/**
 * 列表查询参数
 */
export interface PayOrderQuery {
  pageNum: number;
  pageSize: number;
  // 状态筛选:0 待支付 / 1 已支付 / 2 已退款 / 3 已关闭 / 4 已支付待退款
  status?: number;
  // 商品类型筛选:MEMBERSHIP / BEAN_RECHARGE
  productType?: string;
  // 关键词:匹配订单号或用户 ID
  keyword?: string;
  // 开始时间(yyyy-MM-dd HH:mm:ss)
  beginTime?: string;
  // 结束时间(yyyy-MM-dd HH:mm:ss)
  endTime?: string;
}

// 获取支付订单列表(分页),返回 IPage,调用方读 records / total
export function listPayOrder(params?: Partial<PayOrderQuery>): Promise<any> {
  return request({
    url: '/system/pay-order/list',
    method: 'get',
    params
  });
}

// 获取支付订单详情
export function getPayOrder(id: number | string): Promise<any> {
  return request({
    url: `/system/pay-order/${id}`,
    method: 'get'
  });
}
