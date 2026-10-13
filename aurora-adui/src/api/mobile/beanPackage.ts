/**
 * 豆子充值套餐 API 接口定义(后台 CRUD)
 * url 镜像后端路径,不带 /api 前缀(request 的 baseURL 已为 /api)
 *
 * @author Aurora
 */
import request from '@/utils/request';

/**
 * 豆子充值套餐对象(后端 BeanRechargePackage)
 */
export interface BeanRechargePackage {
  // 主键 ID(编辑/删除按此查询)
  packageId?: number;
  // 套餐名称(与公众平台道具名称对应)
  name?: string;
  // 到账豆数
  beans?: number;
  // 价格(元)
  price?: number;
  // 微信虚拟支付道具 ID(productId)
  virtualProductId?: string;
  // 排序(小在前)
  sort?: number;
  // 状态:0 上架 / 1 下架
  status?: string;
  // 备注
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * 列表查询参数
 */
export interface BeanPackageQuery {
  pageNum: number;
  pageSize: number;
  // 状态筛选:0 上架 / 1 下架
  status?: string;
}

// 获取豆子套餐列表(分页),返回 IPage,调用方读 records / total
export function listBeanPackage(params?: Partial<BeanPackageQuery>): Promise<any> {
  return request({
    url: '/system/mobile/bean-package/list',
    method: 'get',
    params
  });
}

// 获取豆子套餐详情
export function getBeanPackage(id: number | string): Promise<any> {
  return request({
    url: `/system/mobile/bean-package/${id}`,
    method: 'get'
  });
}

// 新增豆子套餐(body 不含 packageId)
export function addBeanPackage(data: BeanRechargePackage) {
  return request({
    url: '/system/mobile/bean-package',
    method: 'post',
    data
  });
}

// 修改豆子套餐(body 含 packageId)
export function updateBeanPackage(data: BeanRechargePackage) {
  return request({
    url: '/system/mobile/bean-package',
    method: 'put',
    data
  });
}

// 删除豆子套餐
export function deleteBeanPackage(id: number | string) {
  return request({
    url: `/system/mobile/bean-package/${id}`,
    method: 'delete'
  });
}
