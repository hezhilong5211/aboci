/**
 * 案例/模板管理 API 接口定义(后台 CRUD + 上下架切换)
 * url 镜像后端路径,不带 /api 前缀(request 的 baseURL 已为 /api)
 *
 * @author Aurora
 */
import request from '@/utils/request';

/**
 * 案例/模板对象
 * params / result_media 后端以 JSON 字符串存储,前端提交前序列化、编辑回填时反序列化
 */
export interface Template {
  templateId?: number;
  // image 图片 / video 视频
  category?: string;
  // 动态行业分类
  industryCategoryId?: number;
  industryCategoryName?: string;
  title?: string;
  // 封面图 URL
  coverUrl?: string;
  // 提示词文本
  prompt?: string;
  // 参数预设(JSON 字符串,结构按 category 区分)
  params?: string;
  // 生成效果媒体 URL 列表(JSON 数组字符串)
  resultMedia?: string;
  // 图生图参考图 URL 列表(JSON 数组字符串,1~4 张,仅 category=image 且 mode=img2img 时有值)
  referenceImages?: string;
  // 标签(逗号分隔)
  tags?: string;
  // 使用次数(展示字段)
  useCount?: number;
  // 排序(小在前)
  sort?: number;
  // 上下架:0 上架 / 1 下架
  status?: string;
  // 本周精选:1 精选 / 0 非精选(list 返回)
  isFeatured?: number;
  createTime?: string;
  updateTime?: string;
}

/**
 * 默认图像模型的画幅比例配置(GET /system/template/imageRatios 返回的 Result.data)
 */
export interface ImageRatios {
  // 默认图像模型名称
  modelName?: string;
  // 模型支持的画幅比例,逗号分隔串(如 "1:1,3:4,16:9"),可能为 null/空
  supportedAspectRatios?: string | null;
}

/**
 * 列表查询参数
 */
export interface TemplateQuery {
  pageNum: number;
  pageSize: number;
  // 分类筛选:image / video
  category?: string;
  industryCategoryId?: number;
  // 状态筛选:0 上架 / 1 下架
  status?: string;
  // 标题关键词(模糊)
  keyword?: string;
}

export interface TemplateCategory {
  categoryId?: number;
  name: string;
  sort: number;
  status: string;
  createTime?: string;
}

/**
 * 分页查询模板列表
 */
export function listTemplate(query: TemplateQuery) {
  return request({
    url: '/system/template/list',
    method: 'get',
    params: query
  });
}

/**
 * 查询模板详情
 */
export function getTemplate(templateId: number) {
  return request({
    url: '/system/template/' + templateId,
    method: 'get'
  });
}

/**
 * 新增模板
 */
export function addTemplate(data: Template) {
  return request({
    url: '/system/template',
    method: 'post',
    data
  });
}

/**
 * 修改模板
 */
export function updateTemplate(data: Template) {
  return request({
    url: '/system/template',
    method: 'put',
    data
  });
}

/**
 * 删除模板(后端逻辑删)
 */
export function removeTemplate(templateId: number) {
  return request({
    url: '/system/template/' + templateId,
    method: 'delete'
  });
}

/**
 * 上下架快捷切换
 */
export function toggleTemplateStatus(templateId: number, status: string) {
  return request({
    url: '/system/template/status',
    method: 'put',
    data: { templateId, status }
  });
}

/**
 * 查询默认图像模型支持的画幅比例
 * 返回 { modelName, supportedAspectRatios },用于抽屉内画幅比例下拉的动态选项
 */
export function getDefaultImageRatios(): Promise<ImageRatios> {
  return request({
    url: '/system/template/imageRatios',
    method: 'get'
  });
}

/**
 * 设置本周精选(整体替换,最多 5 个)
 * body 为模板 id 数组
 */
export function setTemplateFeatured(ids: number[]) {
  return request({
    url: '/system/template/featured',
    method: 'put',
    data: ids
  });
}

export function listTemplateCategory(params: { name?: string; status?: string } = {}) {
  return request({
    url: '/system/template/category/list',
    method: 'get',
    params
  });
}

export function addTemplateCategory(data: TemplateCategory) {
  return request({
    url: '/system/template/category',
    method: 'post',
    data
  });
}

export function updateTemplateCategory(data: TemplateCategory) {
  return request({
    url: '/system/template/category',
    method: 'put',
    data
  });
}

export function removeTemplateCategory(categoryId: number) {
  return request({
    url: '/system/template/category/' + categoryId,
    method: 'delete'
  });
}
