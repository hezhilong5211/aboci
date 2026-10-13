import MarkdownIt from 'markdown-it'

// AI 回复 markdown 渲染器:markdown 文本 → html,交给 mp-html 渲染
// html:false 禁原始 HTML 防注入;breaks 单换行转 <br>;linkify 自动识别链接
const md = new MarkdownIt({
	html: false,
	breaks: true,
	linkify: true,
})

// 渲染结果缓存:key=markdown 原文,value=html。
// 已完成/历史消息文本稳定,命中缓存直接返回,避免流式期间对同一文本逐帧重复解析。
const renderCache = new Map()
const RENDER_CACHE_MAX = 200

/**
 * markdown 文本转 html(给 mp-html 的 :content)
 * @param {string} text markdown 原文(流式累积的 AI 回复)
 * @returns {string} html;解析失败兜底返回原文,不阻断显示
 */
export function renderMarkdown(text) {
	if (!text) return ''
	const cached = renderCache.get(text)
	if (cached !== undefined) return cached
	let html
	try {
		html = md.render(text)
	} catch (e) {
		html = text
	}
	// 超出上限时淘汰最早一条,控制缓存体积
	if (renderCache.size >= RENDER_CACHE_MAX) {
		renderCache.delete(renderCache.keys().next().value)
	}
	renderCache.set(text, html)
	return html
}
