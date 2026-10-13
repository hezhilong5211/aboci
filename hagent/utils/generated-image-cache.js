/**
 * 生成图片持久缓存。
 * 列表预览图进入可视组件时缓存；原图只在用户主动预览或保存时缓存。
 */

const CACHE_VERSION = 1
const CACHE_INDEX_KEY = `aurora_generated_image_cache_v${CACHE_VERSION}`
const CACHE_DIRECTORY_NAME = `generated-image-cache-v${CACHE_VERSION}`
const CACHE_LIMIT_BYTES = 50 * 1024 * 1024
const CACHE_IDLE_MS = 30 * 24 * 60 * 60 * 1000

const pendingDownloads = new Map()
let index = { version: CACHE_VERSION, entries: {} }
let fileSystem = null
let cacheDirectory = ''
let initialization = null
let persistTimer = null

function isRemoteUrl(url) {
	return /^https?:\/\//i.test(url || '')
}

function normalizeUserId(userId) {
	return userId == null || userId === '' ? 'guest' : String(userId)
}

function entryKey(url, userId, type) {
	return `${normalizeUserId(userId)}|${type}|${url}`
}

function hashText(text) {
	let hash = 2166136261
	for (let i = 0; i < text.length; i += 1) {
		hash ^= text.charCodeAt(i)
		hash = Math.imul(hash, 16777619)
	}
	return (hash >>> 0).toString(16)
}

function fileNameFor(url, userId, type) {
	const withoutQuery = String(url).split('?')[0]
	const originalName = withoutQuery.slice(withoutQuery.lastIndexOf('/') + 1)
	const safeName = originalName.replace(/[^a-zA-Z0-9._-]/g, '_').slice(-80) || 'image'
	return `${type}-${hashText(`${normalizeUserId(userId)}|${url}`)}-${safeName}`
}

function loadIndex() {
	try {
		const saved = uni.getStorageSync(CACHE_INDEX_KEY)
		if (saved && saved.version === CACHE_VERSION && saved.entries && typeof saved.entries === 'object') {
			return saved
		}
	} catch (e) {
		// 索引损坏时按空缓存恢复，不能阻断图片展示。
	}
	return { version: CACHE_VERSION, entries: {} }
}

function persistIndex() {
	if (persistTimer) {
		clearTimeout(persistTimer)
		persistTimer = null
	}
	try {
		uni.setStorageSync(CACHE_INDEX_KEY, index)
	} catch (e) {
		// 元数据写入失败时保留本次运行内缓存，后续仍可回退远程地址。
	}
}

function schedulePersistIndex() {
	if (persistTimer) return
	persistTimer = setTimeout(() => {
		persistTimer = null
		try {
			uni.setStorageSync(CACHE_INDEX_KEY, index)
		} catch (e) {
			// 访问时间写入失败不影响缓存文件继续使用。
		}
	}, 500)
}

function resolveCacheDirectory() {
	if (typeof wx !== 'undefined' && wx.env && wx.env.USER_DATA_PATH) {
		return `${wx.env.USER_DATA_PATH}/${CACHE_DIRECTORY_NAME}`
	}
	return ''
}

function ensureDirectory(path) {
	return new Promise((resolve) => {
		fileSystem.access({
			path,
			success: () => resolve(true),
			fail: () => {
				fileSystem.mkdir({
					dirPath: path,
					recursive: true,
					success: () => resolve(true),
					fail: () => resolve(false),
				})
			},
		})
	})
}

function fileExists(path) {
	return new Promise((resolve) => {
		fileSystem.access({ path, success: () => resolve(true), fail: () => resolve(false) })
	})
}

function fileSize(path) {
	return new Promise((resolve) => {
		fileSystem.stat({
			path,
			success: (res) => resolve(Number((res.stats && res.stats.size) || res.size || 0)),
			fail: () => resolve(0),
		})
	})
}

function unlink(path) {
	return new Promise((resolve) => {
		fileSystem.unlink({ filePath: path, success: () => resolve(), fail: () => resolve() })
	})
}

function readDirectory(path) {
	return new Promise((resolve) => {
		fileSystem.readdir({
			dirPath: path,
			success: (res) => resolve(Array.isArray(res.files) ? res.files : []),
			fail: () => resolve([]),
		})
	})
}

async function removeEntry(key) {
	const entry = index.entries[key]
	if (!entry) return
	delete index.entries[key]
	if (entry.path) await unlink(entry.path)
}

function totalCacheSize() {
	return Object.values(index.entries).reduce((sum, entry) => sum + Number(entry.size || 0), 0)
}

async function pruneInvalidEntries() {
	const now = Date.now()
	let changed = false
	for (const [key, entry] of Object.entries(index.entries)) {
		const expired = now - Number(entry.lastAccess || 0) > CACHE_IDLE_MS
		const exists = !expired && entry.path && await fileExists(entry.path)
		if (!exists) {
			await removeEntry(key)
			changed = true
		}
	}
	if (changed) persistIndex()
}

async function pruneOrphanFiles() {
	const indexedPaths = new Set(Object.values(index.entries).map((entry) => entry.path).filter(Boolean))
	const files = await readDirectory(cacheDirectory)
	for (const file of files) {
		const path = `${cacheDirectory}/${file}`
		if (!indexedPaths.has(path)) await unlink(path)
	}
}

async function initialize() {
	if (initialization) return initialization
	initialization = (async () => {
		if (typeof uni.getFileSystemManager !== 'function') return false
		cacheDirectory = resolveCacheDirectory()
		if (!cacheDirectory) return false
		fileSystem = uni.getFileSystemManager()
		if (!fileSystem || !(await ensureDirectory(cacheDirectory))) return false
		index = loadIndex()
		await pruneInvalidEntries()
		await pruneOrphanFiles()
		return true
	})()
	return initialization
}

async function evictFor(requiredBytes) {
	let total = totalCacheSize()
	if (total + requiredBytes <= CACHE_LIMIT_BYTES) return
	const candidates = Object.entries(index.entries).sort(([, left], [, right]) => {
		if (left.type !== right.type) return left.type === 'original' ? -1 : 1
		return Number(left.lastAccess || 0) - Number(right.lastAccess || 0)
	})
	for (const [key, entry] of candidates) {
		if (total + requiredBytes <= CACHE_LIMIT_BYTES) break
		total -= Number(entry.size || 0)
		await removeEntry(key)
	}
	persistIndex()
}

function download(url, filePath) {
	return new Promise((resolve, reject) => {
		uni.downloadFile({
			url,
			filePath,
			success: (res) => {
				const downloadedPath = res.filePath || res.tempFilePath
				if (res.statusCode === 200 && downloadedPath) resolve(downloadedPath)
				else reject(new Error('图片下载失败'))
			},
			fail: reject,
		})
	})
}

async function findCachedPath(key) {
	const entry = index.entries[key]
	if (!entry) return ''
	if (!(await fileExists(entry.path))) {
		delete index.entries[key]
		persistIndex()
		return ''
	}
	entry.lastAccess = Date.now()
	schedulePersistIndex()
	return entry.path
}

async function cacheRemoteImage(url, userId, type, key) {
	const targetPath = `${cacheDirectory}/${fileNameFor(url, userId, type)}`
	try {
		if (await fileExists(targetPath)) await unlink(targetPath)
		const savedPath = await download(url, targetPath)
		const size = await fileSize(savedPath)
		if (size <= 0 || size > CACHE_LIMIT_BYTES) {
			await unlink(savedPath)
			return url
		}
		await evictFor(size)
		index.entries[key] = {
			url,
			userId: normalizeUserId(userId),
			type,
			path: savedPath,
			size,
			lastAccess: Date.now(),
		}
		// 多图并发下载时再次按实际总量淘汰，确保缓存最终不超过上限。
		await evictFor(0)
		persistIndex()
		return index.entries[key] ? savedPath : url
	} catch (e) {
		await unlink(targetPath)
		if (process.env.NODE_ENV === 'development') {
			console.warn('[图片缓存] 持久化失败', e)
		}
		return url
	}
}

/** 只读取已有缓存，不触发网络下载。 */
export async function findGeneratedImageCache(url, { userId, type = 'preview' } = {}) {
	if (!url || !isRemoteUrl(url) || !(await initialize())) return ''
	const normalizedType = type === 'original' ? 'original' : 'preview'
	return findCachedPath(entryKey(url, userId, normalizedType))
}

/**
 * 返回可展示的本地缓存路径；未命中时下载并持久化，失败则返回原远程地址。
 */
export async function resolveGeneratedImage(url, { userId, type = 'preview' } = {}) {
	if (!url || !isRemoteUrl(url)) return url || ''
	if (!(await initialize())) return url
	const normalizedType = type === 'original' ? 'original' : 'preview'
	const key = entryKey(url, userId, normalizedType)
	const cached = await findCachedPath(key)
	if (cached) return cached
	if (pendingDownloads.has(key)) return pendingDownloads.get(key)
	const task = cacheRemoteImage(url, userId, normalizedType, key)
		.finally(() => pendingDownloads.delete(key))
	pendingDownloads.set(key, task)
	return task
}

/** 删除单个失效缓存，下一次显示会重新下载。 */
export async function invalidateGeneratedImage(url, { userId, type = 'preview' } = {}) {
	if (!url || !(await initialize())) return
	const normalizedType = type === 'original' ? 'original' : 'preview'
	const key = entryKey(url, userId, normalizedType)
	await removeEntry(key)
	persistIndex()
}

/**
 * 用户点击后立即预览，避免超过微信 3 秒手势窗口；当前原图在后台缓存。
 */
export async function previewGeneratedImages(urls, currentIndex, userId) {
	const validUrls = (Array.isArray(urls) ? urls : []).filter(Boolean)
	if (!validUrls.length) return
	const index = Math.max(0, Math.min(Number(currentIndex) || 0, validUrls.length - 1))
	const current = validUrls[index]
	uni.previewImage({ urls: validUrls, current })
	try {
		await resolveGeneratedImage(current, { userId, type: 'original' })
	} catch (e) {
		// 缓存失败不能影响已经打开的图片预览。
	}
}

/** 供开发者工具核验缓存数量和体积，不暴露文件内容。 */
export async function getGeneratedImageCacheStats() {
	if (!(await initialize())) return { count: 0, size: 0, limit: CACHE_LIMIT_BYTES }
	return {
		count: Object.keys(index.entries).length,
		size: totalCacheSize(),
		limit: CACHE_LIMIT_BYTES,
	}
}
