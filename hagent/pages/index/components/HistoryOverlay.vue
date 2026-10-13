<template>
	<!-- 历史记录全屏浮层:对应设计屏 09。fixed 覆盖,由父组件 visible 控制 -->
	<view class="history-overlay" :class="{ 'history-overlay--closing': closing }" v-if="visible">
		<ConversationTopNav
			mode="history"
			:status-bar-height="navigationMetrics.statusBarHeight"
			:navigation-bar-height="navigationMetrics.navigationBarHeight"
			:right-safe-width="navigationMetrics.rightSafeWidth"
			@back="close"
		/>

		<!-- 主体滚动区 -->
		<scroll-view class="history-scroll" scroll-y :show-scrollbar="false">
			<view class="history-inner">
				<!-- 新建对话 -->
				<view
					class="history-new"
					:class="{ 'history-new--loading': creating, 'history-new--disabled': switching }"
					@tap="createNew"
				>
					<text class="history-new-icon">＋</text>
					<text class="history-new-text">{{ creating ? '正在新建…' : '新建对话' }}</text>
				</view>

				<!-- 加载态 -->
				<view v-if="loading" class="history-loading">
					<text class="history-loading-text">加载中…</text>
				</view>

				<!-- 空态 -->
				<am-empty v-else-if="!hasAny" title="还没有历史对话" desc="开启一段新对话吧" />

				<!-- 分组列表 -->
				<template v-else>
					<view class="history-group" v-for="group in nonEmptyGroups" :key="group.key">
						<text class="history-group-label">{{ group.label }}</text>
						<view class="history-card">
							<view
									class="history-row"
									:class="{
										'history-row--first': idx === 0,
										'history-row--loading': isSwitching(item),
										'history-row--disabled': switching,
									}"
								v-for="(item, idx) in group.items"
								:key="item.conversationId"
								@tap="pick(item)"
								>
									<text class="history-row-title">{{ item.title || '未命名对话' }}</text>
									<text class="history-row-time">
										{{ isSwitching(item) ? '切换中…' : item.timeLabel }}
									</text>
							</view>
						</view>
					</view>
				</template>
			</view>
		</scroll-view>
	</view>
</template>

<script setup>
import { computed } from 'vue'
import ConversationTopNav from './ConversationTopNav.vue'

const props = defineProps({
	// 是否显示
	visible: { type: Boolean, default: false },
	// 首页统一计算的状态栏、导航栏与微信胶囊安全尺寸
	navigationMetrics: {
		type: Object,
		default: () => ({
			statusBarHeight: 20,
			navigationBarHeight: 44,
			rightSafeWidth: 96,
		}),
	},
	// 会话列表(ConversationVO 数组)
	conversations: { type: Array, default: () => [] },
	// 加载态
	loading: { type: Boolean, default: false },
	// 正在切换的会话编号
	switchingConversationId: { type: [String, Number], default: null },
	// 是否正在新建对话
	creating: { type: Boolean, default: false },
	// 父组件已完成目标渲染，浮层正在淡出
	closing: { type: Boolean, default: false },
})

const emit = defineEmits(['close', 'create-new', 'pick'])

// 解析 LocalDateTime 字符串为 Date(兼容 'T' 分隔与空格分隔)
function parseTime(str) {
	if (!str) return null
	const d = new Date(String(str).replace('T', ' ').replace(/-/g, '/'))
	return isNaN(d.getTime()) ? null : d
}

// 两位补零
function pad(n) {
	return n < 10 ? '0' + n : '' + n
}

// 时间标签:今天 HH:mm / 昨天 HH:mm / N 天前
function buildTimeLabel(date, dayDiff) {
	const hm = pad(date.getHours()) + ':' + pad(date.getMinutes())
	if (dayDiff === 0) return hm
	if (dayDiff === 1) return '昨天 ' + hm
	if (dayDiff === 2) return '前天 ' + hm
	return dayDiff + ' 天前'
}

// 计算与今天相差的天数(按自然日)
function diffDays(date) {
	const today = new Date()
	const a = new Date(today.getFullYear(), today.getMonth(), today.getDate())
	const b = new Date(date.getFullYear(), date.getMonth(), date.getDate())
	return Math.round((a - b) / 86400000)
}

// 分组:今天 / 近 7 天 / 更早
const groups = computed(() => {
	const today = { key: 'today', label: '今天', items: [] }
	const week = { key: 'week', label: '近 7 天', items: [] }
	const earlier = { key: 'earlier', label: '更早', items: [] }

	for (const c of props.conversations) {
		const date = parseTime(c.lastMessageTime || c.createTime)
		const dayDiff = date ? diffDays(date) : 999
		const item = {
			conversationId: c.conversationId,
			title: c.title,
			timeLabel: date ? buildTimeLabel(date, dayDiff) : '',
		}
		if (dayDiff <= 0) today.items.push(item)
		else if (dayDiff <= 7) week.items.push(item)
		else earlier.items.push(item)
	}
	return [today, week, earlier]
})

// 仅保留非空分组,避免渲染空标题
const nonEmptyGroups = computed(() => groups.value.filter((g) => g.items.length > 0))

// 是否有任意会话
const hasAny = computed(() => props.conversations.length > 0)
const switching = computed(
	() => props.creating || props.switchingConversationId != null || props.closing
)

function isSwitching(item) {
	return (
		props.switchingConversationId != null &&
		String(props.switchingConversationId) === String(item.conversationId)
	)
}

function close() {
	if (switching.value) return
	emit('close')
}

function createNew() {
	if (switching.value) return
	emit('create-new')
}

function pick(item) {
	if (switching.value) return
	emit('pick', item)
}
</script>

<style lang="scss" scoped>
.history-overlay {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	bottom: 0;
	z-index: 900;
	background: #f6f7fb;
	display: flex;
	flex-direction: column;
	opacity: 1;
	transition: opacity 0.12s ease-out;
}

.history-overlay--closing {
	opacity: 0;
	pointer-events: none;
}

/* 滚动区 */
.history-scroll {
	flex: 1;
	min-height: 0;
}

.history-inner {
	padding: 24rpx 36rpx calc(60rpx + env(safe-area-inset-bottom));
}

/* 新建对话 */
.history-new {
	height: 108rpx;
	border-radius: 32rpx;
	background: linear-gradient(135deg, #4f84ea, #8fb4fb);
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 16rpx;
	box-shadow: 0 12rpx 32rpx rgba(96, 140, 224, 0.3);
	margin-bottom: 40rpx;
}

.history-new:active {
	opacity: 0.92;
}

.history-new--loading {
	background: linear-gradient(135deg, #6b96e8, #9ab9f3);
}

.history-new--disabled {
	pointer-events: none;
}

.history-new-icon {
	font-size: 40rpx;
	color: #fff;
	line-height: 1;
}

.history-new-text {
	font-size: 32rpx;
	font-weight: 700;
	color: #fff;
}

.history-loading {
	padding: 60rpx 0;
	text-align: center;
}

.history-loading-text {
	font-size: 26rpx;
	color: #94a3b8;
}

/* 分组 */
.history-group {
	margin-bottom: 36rpx;
}

.history-group-label {
	display: block;
	font-size: 26rpx;
	color: #94a3b8;
	margin: 0 8rpx 20rpx;
}

/* 会话卡:科技描边 */
.history-card {
	border-radius: 28rpx;
	overflow: hidden;
	background: linear-gradient(#fff, #fff) padding-box,
		linear-gradient(135deg, #7fb0f7, #a0c4fb) border-box;
	border: 3rpx solid transparent;
}

.history-row {
	display: flex;
	align-items: center;
	padding: 28rpx 32rpx;
	border-top: 1rpx solid #eef2f8;
}

.history-row--first {
	border-top: none;
}

.history-row:active {
	background: #f4f7fc;
}

.history-row--loading {
	background: #f4f7fc;
}

.history-row--disabled {
	pointer-events: none;
}

.history-row-title {
	flex: 1;
	min-width: 0;
	font-size: 30rpx;
	color: #1e293b;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.history-row-time {
	flex-shrink: 0;
	margin-left: 20rpx;
	font-size: 24rpx;
	color: #94a3b8;
}
</style>
