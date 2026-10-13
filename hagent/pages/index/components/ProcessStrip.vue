<template>
	<!-- 过程条:把「思考」与「工具调用」合并为一条可折叠胶囊。对应设计屏 04 / 04B -->
	<!-- 排队、thinking 与 tools 全空时整条不渲染 -->
	<view class="process-strip" v-if="queueMessage || thinking || (tools && tools.length)">
		<!-- 折叠态标题:渐变描边胶囊 + 脑图标 + 状态文案 + chevron,点击切换展开 -->
		<view class="strip-head" :class="{ 'strip-head--queue': queueMessage }" @tap="toggle">
			<!-- 脑图标 -->
			<view class="strip-icon">
				<text class="strip-icon-glyph">{{ queueMessage ? '⏳' : '🧠' }}</text>
			</view>
			<text class="strip-title">{{ headText }}</text>
			<!-- 折叠箭头:展开时朝上 -->
			<text v-if="!queueMessage" class="strip-arrow" :class="{ 'strip-arrow--open': expanded }">⌄</text>
		</view>

		<!-- 展开态 -->
		<view class="strip-body" v-if="expanded && !queueMessage">
			<!-- 上半:思考文本(左竖条 + 浅蓝底),空则不显示 -->
			<view class="strip-thinking" v-if="thinking">
				<text class="strip-thinking-text am-selectable-text" user-select>{{ thinking }}</text>
			</view>

			<!-- 下半:工具列表 -->
			<view class="strip-tools" v-if="tools && tools.length">
				<view class="strip-tool" v-for="(tool, idx) in tools" :key="idx">
					<!-- running 转圈 / done 勾选 / failed 叉号 -->
					<view v-if="tool.status === 'running'" class="strip-tool-spinner" />
					<text v-else-if="tool.status === 'failed'" class="strip-tool-failed">×</text>
					<text v-else class="strip-tool-check">✓</text>
					<text class="strip-tool-name">{{ toolText(tool) }}</text>
				</view>
			</view>
		</view>
	</view>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
	// 超过等待提醒阈值时由后端下发的排队状态
	queueMessage: { type: String, default: '' },
	// 思考增量文本(由父组件随 onThinking 拼接)
	thinking: { type: String, default: '' },
	// 工具数组,元素 { name, status:'running'|'done', summary, n? }
	tools: { type: Array, default: () => [] },
	// 本条是否仍在流式输出
	streaming: { type: Boolean, default: false },
	// 本条是否已产出结果(配合状态判断「完成」)
	hasResult: { type: Boolean, default: false },
})

// 默认折叠:折叠头按状态轮换显示「正在思考…/正在生成图片…/已思考」,点击下拉才展开思考内容与工具列表
const expanded = ref(false)

function toggle() {
	if (props.queueMessage) return
	expanded.value = !expanded.value
}

// 工具名中文映射
function cnToolName(name) {
	if (name === 'recognize_image') return '识别图片'
	if (name === 'generate_image') return '生成图片'
	if (name === 'generate_images') return '生成图片'
	return '处理'
}

// 折叠态状态文案:按优先级轮换
const headText = computed(() => {
	if (props.queueMessage) return props.queueMessage
	const list = props.tools || []
	// 1) 有 running 工具 → 正在{中文名}…
	const running = list.find((t) => t.status === 'running')
	if (running) return '正在' + cnToolName(running.name) + '…'
	const failed = list.find((t) => t.status === 'failed')
	if (failed) return cnToolName(failed.name) + '失败'
	// 2) 仍在流式且思考在增长 → 正在思考…
	if (props.streaming && props.thinking) return '正在思考…'
	// 3) 完成态
	if (list.length) return '已思考 · 调用 ' + list.length + ' 个工具'
	return '已思考'
})

// 展开态单个工具文案:优先后端 summary,否则中文名兜底
function toolText(tool) {
	if (tool.status === 'failed') return cnToolName(tool.name) + '暂未完成，请重试'
	if (tool.summary) return tool.summary
	const cn = cnToolName(tool.name)
	if (tool.status === 'running') return '正在' + cn + '…'
	return '已' + cn
}
</script>

<style lang="scss" scoped>
.process-strip {
	width: 100%;
}

/* 折叠态标题:渐变描边胶囊 */
.strip-head {
	display: flex;
	align-items: center;
	gap: 16rpx;
	padding: 20rpx 28rpx;
	border-radius: 24rpx;
	/* 内白外科技渐变描边 */
	background: linear-gradient(#fff, #fff) padding-box,
		linear-gradient(135deg, #7fb0f7, #a0c4fb) border-box;
	border: 3rpx solid transparent;
}

.strip-head:active {
	opacity: 0.92;
}

.strip-head--queue {
	background: #f8fafc;
	border-color: #dbe4f0;
}

.strip-icon {
	width: 30rpx;
	height: 30rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.strip-icon-glyph {
	font-size: 26rpx;
	line-height: 1;
}

.strip-title {
	flex: 1;
	min-width: 0;
	font-size: 26rpx;
	color: #64748b;
	line-height: 1.4;
}

.strip-arrow {
	flex-shrink: 0;
	font-size: 32rpx;
	color: #94a3b8;
	line-height: 1;
	transition: transform 0.2s ease;
}

.strip-arrow--open {
	transform: rotate(180deg);
	color: #4f84ea;
}

/* 展开态容器 */
.strip-body {
	display: flex;
	flex-direction: column;
	gap: 16rpx;
	margin-top: 16rpx;
}

/* 思考文本:左竖条 + 浅蓝底 */
.strip-thinking {
	padding: 24rpx 28rpx;
	border-left: 2rpx solid #c7d6f2;
	background: #f4f7fc;
	border-radius: 0 20rpx 20rpx 0;
}

.strip-thinking-text {
	font-size: 26rpx;
	line-height: 1.85;
	color: #64748b;
	white-space: pre-wrap;
	word-break: break-word;
}

/* 工具列表 */
.strip-tools {
	display: flex;
	flex-direction: column;
	gap: 12rpx;
	padding: 20rpx 28rpx;
	border-radius: 20rpx;
	background: #deeafb;
	border: 1rpx solid rgba(96, 140, 224, 0.18);
}

.strip-tool {
	display: flex;
	align-items: center;
	gap: 16rpx;
}

/* 转圈加载 */
.strip-tool-spinner {
	width: 28rpx;
	height: 28rpx;
	border: 4rpx solid rgba(96, 140, 224, 0.35);
	border-top-color: #4f84ea;
	border-radius: 50%;
	flex-shrink: 0;
	animation: strip-spin 0.8s linear infinite;
}

@keyframes strip-spin {
	to {
		transform: rotate(360deg);
	}
}

.strip-tool-check {
	width: 28rpx;
	height: 28rpx;
	line-height: 28rpx;
	text-align: center;
	font-size: 24rpx;
	font-weight: 700;
	color: #16a34a;
	flex-shrink: 0;
}

.strip-tool-failed {
	font-size: 28rpx;
	font-weight: 700;
	color: #ef4444;
	line-height: 1;
}

.strip-tool-name {
	flex: 1;
	min-width: 0;
	font-size: 26rpx;
	font-weight: 600;
	color: #4f84ea;
	line-height: 1.4;
}
</style>
