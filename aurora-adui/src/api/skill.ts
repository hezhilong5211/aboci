/**
 * Skill 配置管理 API 接口定义(后台 CRUD + 启停切换)
 * url 镜像后端路径,不带 /api 前缀(request 的 baseURL 已为 /api)
 *
 * @author Aurora
 */
import request from '@/utils/request';

/**
 * Skill 对象
 * capabilities 后端以逗号分隔字符串存储(text/vision/image/video),前端提交前拼接、编辑回填时拆分
 */
export interface Skill {
  skillId?: number;
  name?: string;
  // 图标 URL
  icon?: string;
  // 说明
  description?: string;
  // 注入提示词(送给大模型的原始文本)
  injectPrompt?: string;
  // 可用能力范围(逗号分隔:text/vision/image/video)
  capabilities?: string;
  // 排序(小在前)
  sort?: number;
  // 状态:0 启用 / 1 停用
  status?: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * 列表查询参数
 */
export interface SkillQuery {
  pageNum: number;
  pageSize: number;
  // 名称关键词(模糊)
  name?: string;
  // 状态筛选:0 启用 / 1 停用
  status?: string;
}

/**
 * 分页查询 Skill 列表(name 模糊 + status 筛选,按 sort)
 */
export function listSkill(query: SkillQuery) {
  return request({
    url: '/system/skill/list',
    method: 'get',
    params: query
  });
}

/**
 * 查询 Skill 详情
 */
export function getSkill(skillId: number) {
  return request({
    url: '/system/skill/' + skillId,
    method: 'get'
  });
}

/**
 * 新增 Skill
 */
export function addSkill(data: Skill) {
  return request({
    url: '/system/skill',
    method: 'post',
    data
  });
}

/**
 * 修改 Skill
 */
export function updateSkill(data: Skill) {
  return request({
    url: '/system/skill',
    method: 'put',
    data
  });
}

/**
 * 删除 Skill(后端逻辑删)
 */
export function removeSkill(skillId: number) {
  return request({
    url: '/system/skill/' + skillId,
    method: 'delete'
  });
}

/**
 * 启停快捷切换
 */
export function toggleSkillStatus(skillId: number, status: string) {
  return request({
    url: '/system/skill/status',
    method: 'put',
    data: { skillId, status }
  });
}
