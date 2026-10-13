/**
 * 案例/模板相关 API
 * 对接后端 MobileTemplateController（/api/mobile/template，公开只读，未登录可浏览）
 */

import { get, post } from './request.js'

/**
 * 案例列表分页（仅上架，按 sort 升序）
 * @param {Object} [params] - { category?(image/video), pageNum=1, pageSize=10 }
 * @returns {Promise<Object>} - IPage，含 records（TemplateVO 数组）、total
 */
export function listTemplates(params = {}) {
	const { category, industryCategoryId, pageNum = 1, pageSize = 10 } = params
	const query = { pageNum, pageSize }
	if (category) query.category = category
	if (industryCategoryId != null) query.industryCategoryId = industryCategoryId
	return get('/api/mobile/template/list', query)
}

/**
 * 精选案例（顶部轮播用,精选≤5,无精选则兜底最早5条,无数据返回空数组）
 * @param {Object} [params] - { category?(image/video) }
 * @returns {Promise<Array>} - TemplateVO 数组
 */
export function getFeaturedTemplates(params = {}) {
	const { category, industryCategoryId } = params
	const query = {}
	if (category) query.category = category
	if (industryCategoryId != null) query.industryCategoryId = industryCategoryId
	return get('/api/mobile/template/featured', query)
}

/** 当前媒体类型下存在上架案例的启用行业分类。 */
export function getTemplateCategories(mediaType = 'image') {
	return get('/api/mobile/template/categories', { mediaType })
}

/**
 * 案例详情
 * @param {string|number} id - 模板 id
 */
export function getTemplate(id) {
	return get('/api/mobile/template/' + id)
}

/**
 * 使用计数 +1（「用此模板去生成」带入时调用）
 * @param {string|number} id - 模板 id
 */
export function useTemplate(id) {
	return post('/api/mobile/template/' + id + '/use')
}
