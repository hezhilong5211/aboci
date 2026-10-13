/**
 * Agent 对话 SSE 流式封装
 *
 * 后端契约：
 *   POST {baseUrl}/api/mobile/agent/chat/stream
 *   header: Authorization Bearer <token> + Content-Type application/json
 *   body:   { conversationId?, modelId?, message, imageInputs?, skill?, clarificationScopeId?, clarificationOptionId? }
 *   响应：  SSE 流，每帧形如 'data:{json}\n\n'
 *           json.type ∈ meta / queue / thinking / text / tool / clarification / result / done / error
 *             - meta     { conversationId, messageId }
 *             - queue    { message }  排队等待提示（不写入对话历史）
 *             - thinking { content }  思考增量（reasoner，拼接渲染折叠思考块）
 *             - text     { content }
 *             - tool     { name, status, summary }
 *             - clarification { clarification }
 *             - result   { data }
 *             - done
 *             - error    { message, errorCode?, action? }
 *
 * 平台差异：
 *   - 微信小程序：uni.request enableChunked + onChunkReceived 分块接收
 *   - H5：fetch + ReadableStream getReader 流式读
 */

import config from '@/utils/config.js'
import { getStorage } from '@/utils/storage.js'

/**
 * 将一帧 SSE 文本（已去掉首尾空白）解析并分发到对应回调
 * 容错：解析失败的帧忽略，不抛异常
 * @param {string} frame - 单帧文本，形如 'data:{json}'
 * @param {Object} handlers - 各类型回调集合
 */
function dispatchFrame(frame, handlers) {
	// 去掉 SSE 前缀 'data:'（兼容 'data: ' 带空格）
	let payload = frame
	const idx = payload.indexOf('data:')
	if (idx === -1) return
	payload = payload.slice(idx + 5).trim()
	if (!payload) return

	let json
	try {
		json = JSON.parse(payload)
	} catch (e) {
		// 解析失败的帧忽略，不影响后续帧
		return
	}

	const { onMeta, onQueue, onThinking, onText, onTool, onClarification, onResult, onDone, onError } = handlers
	switch (json.type) {
		case 'meta':
			onMeta && onMeta(json)
			break
		case 'queue':
			onQueue && onQueue(json)
			break
		case 'thinking':
			onThinking && onThinking(json)
			break
		case 'text':
			onText && onText(json)
			break
		case 'tool':
			onTool && onTool(json)
			break
		case 'clarification':
			onClarification && onClarification(json)
			break
		case 'result':
			onResult && onResult(json)
			break
		case 'done':
			onDone && onDone(json)
			break
		case 'error':
			onError && onError(json)
			break
		default:
			// 未知类型忽略
			break
	}
}

/**
 * 创建帧解析器：内部维护字符串缓冲，按 '\n\n' 切帧，保留未完整的尾部半包
 * @param {Object} handlers - 各类型回调集合
 * @returns {Function} 接收新文本片段的函数
 */
function createFrameParser(handlers) {
	let buffer = ''
	return function feed(chunk) {
		buffer += chunk
		let sepIndex
		// 循环切出所有完整帧（以 '\n\n' 分隔）
		while ((sepIndex = buffer.indexOf('\n\n')) !== -1) {
			const frame = buffer.slice(0, sepIndex).trim()
			buffer = buffer.slice(sepIndex + 2)
			if (frame) {
				dispatchFrame(frame, handlers)
			}
		}
	}
}

/**
 * 发起 Agent 对话流式请求
 * @param {Object} options
 * @param {Object} options.data - 请求体 { conversationId?, modelId?, message, imageInputs?, skill?, clarificationScopeId?, clarificationOptionId? }
 * @param {Function} [options.onMeta]     - 收到 meta 帧 { conversationId, messageId }
 * @param {Function} [options.onQueue]    - 收到 queue 帧 { message }
 * @param {Function} [options.onThinking] - 收到 thinking 帧 { content }（思考增量）
 * @param {Function} [options.onText]   - 收到 text 帧 { content }
 * @param {Function} [options.onTool]   - 收到 tool 帧 { name, status, summary }
 * @param {Function} [options.onClarification] - 收到 clarification 帧 { clarification }
 * @param {Function} [options.onResult] - 收到 result 帧 { data }
 * @param {Function} [options.onDone]   - 收到 done 帧
 * @param {Function} [options.onError]  - 收到 error 帧或发生网络/请求错误 { message }
 * @returns {Object} 含 abort() 的对象（小程序下为 uni.request task，已附带 abort）
 */
export function agentChatStream({ endpoint = '/api/mobile/agent/chat/stream', data, onMeta, onQueue, onThinking, onText, onTool, onClarification, onResult, onDone, onError } = {}) {
	const url = `${config.baseUrl}${endpoint}`
	const token = getStorage(config.tokenKey)
	const header = {
		'Content-Type': 'application/json'
	}
	if (token) {
		header['Authorization'] = `Bearer ${token}`
	}

	let settled = false
	const failOnce = (event) => { if (settled) return; settled = true; onError && onError(event) }
	const handlers = { onMeta, onQueue, onThinking, onText, onTool, onClarification, onResult,
		onDone: (event) => { if (settled) return; settled = true; onDone && onDone(event) },
		onError: failOnce, isSettled: () => settled,
	}

	// #ifndef H5
	return startMpStream({ url, header, data, handlers, onError: failOnce })
	// #endif

	// #ifdef H5
	return startH5Stream({ url, header, data, handlers, onError: failOnce })
	// #endif
}

// #ifndef H5
/**
 * 微信小程序 / App 端：uni.request enableChunked + onChunkReceived
 */
function startMpStream({ url, header, data, handlers, onError }) {
	const feed = createFrameParser(handlers)
	let aborted = false
	const streamTimeout = 10 * 60 * 1000
	// 用于把 ArrayBuffer 解码为字符串（优先 TextDecoder，缺失则手写 UTF-8 解码兜底）
	const decode = createChunkDecoder()

	const task = uni.request({
		url,
		method: 'POST',
		enableChunked: true,
		timeout: streamTimeout,
		header,
		data,
		success: (response) => {
			if (aborted || handlers.isSettled()) return
			const body = response.data && typeof response.data === 'object' ? response.data : null
			const rejected = response.statusCode >= 400 || (body?.code != null && body.code !== 200)
			onError && onError({ type: rejected ? 'error' : undefined,
				message: body?.msg || body?.message || (rejected ? `请求失败(${response.statusCode})` : '连接结束，正在恢复任务状态') })
		},
		fail: (err) => {
			if (aborted) return
			onError && onError({ message: (err && err.errMsg) || '网络异常' })
		}
	})

	// 分块接收回调：res.data 为本次到达的 ArrayBuffer
	task.onChunkReceived((res) => {
		if (aborted) return
		try {
			const text = decode(res.data)
			if (text) feed(text)
		} catch (e) {
			// 单次分块解码异常忽略，等待后续分块
		}
	})

	return {
		abort() {
			if (aborted) return
			aborted = true
			task.abort()
		}
	}
}

/**
 * 构造分块解码器：优先使用 TextDecoder（stream 模式可正确处理跨块的多字节字符），
 * 不可用时退回手写 UTF-8 解码
 * @returns {Function} (ArrayBuffer|Uint8Array) => string
 */
function createChunkDecoder() {
	if (typeof TextDecoder !== 'undefined') {
		const decoder = new TextDecoder('utf-8')
		return (buf) => decoder.decode(toUint8(buf), { stream: true })
	}
	return (buf) => utf8Decode(toUint8(buf))
}

/**
 * 将 ArrayBuffer / TypedArray 统一转为 Uint8Array
 */
function toUint8(buf) {
	if (buf instanceof Uint8Array) return buf
	return new Uint8Array(buf)
}

/**
 * 手写 UTF-8 解码兜底（TextDecoder 不可用时）
 * 注意：不处理跨块截断的多字节字符，仅作为极端兜底
 */
function utf8Decode(bytes) {
	let result = ''
	let i = 0
	const len = bytes.length
	while (i < len) {
		const byte1 = bytes[i++]
		if (byte1 < 0x80) {
			result += String.fromCharCode(byte1)
		} else if (byte1 >= 0xc0 && byte1 < 0xe0) {
			const byte2 = bytes[i++] & 0x3f
			result += String.fromCharCode(((byte1 & 0x1f) << 6) | byte2)
		} else if (byte1 >= 0xe0 && byte1 < 0xf0) {
			const byte2 = bytes[i++] & 0x3f
			const byte3 = bytes[i++] & 0x3f
			result += String.fromCharCode(((byte1 & 0x0f) << 12) | (byte2 << 6) | byte3)
		} else {
			// 4 字节字符（增补平面），转为代理对
			const byte2 = bytes[i++] & 0x3f
			const byte3 = bytes[i++] & 0x3f
			const byte4 = bytes[i++] & 0x3f
			let codepoint = ((byte1 & 0x07) << 18) | (byte2 << 12) | (byte3 << 6) | byte4
			codepoint -= 0x10000
			result += String.fromCharCode(0xd800 + (codepoint >> 10), 0xdc00 + (codepoint & 0x3ff))
		}
	}
	return result
}
// #endif

// #ifdef H5
/**
 * H5 端：fetch + ReadableStream getReader 流式读
 */
function startH5Stream({ url, header, data, handlers, onError }) {
	const feed = createFrameParser(handlers)
	const controller = new AbortController()

	fetch(url, {
		method: 'POST',
		headers: header,
		body: JSON.stringify(data),
		signal: controller.signal
	})
		.then((res) => {
			if (!res.ok || !res.body) {
				onError && onError({ type: 'error', message: `请求失败(${res.status})` })
				return
			}
			const reader = res.body.getReader()
			const decoder = new TextDecoder('utf-8')

			const read = () => {
				reader.read().then(({ done, value }) => {
					if (done) {
						// 冲洗 decoder 残留
						const tail = decoder.decode()
						if (tail) feed(tail)
						if (!handlers.isSettled()) onError && onError({ message: '连接结束，正在恢复任务状态' })
						return
					}
					const text = decoder.decode(value, { stream: true })
					if (text) feed(text)
					read()
				}).catch((err) => {
					// 主动 abort 触发的取消不视为错误
					if (err && err.name === 'AbortError') return
					onError && onError({ message: (err && err.message) || '读取流异常' })
				})
			}
			read()
		})
		.catch((err) => {
			if (err && err.name === 'AbortError') return
			onError && onError({ message: (err && err.message) || '网络异常' })
		})

	return {
		abort() {
			controller.abort()
		}
	}
}
// #endif
