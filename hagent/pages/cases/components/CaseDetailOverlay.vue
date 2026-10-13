<template>
	<!-- 案例详情浮层:全屏 fixed,非沉浸(顶部留状态栏安全区) -->
	<view class="detail-overlay" :style="{ paddingTop: statusBarHeight + 'px' }">
		<!-- 顶栏行:返回按钮(对齐胶囊安全区高度) -->
		<view class="detail-topbar">
			<view class="detail-back" @tap="$emit('close')">
				<view class="detail-back__arrow" />
			</view>
		</view>

		<scroll-view class="detail-scroll" scroll-y :show-scrollbar="false">
			<!-- 顶部大图区:容器按画幅(params.aspectRatio)固定占位,图 aspectFill 填入,加载不跳动;左右留白 + 圆角 -->
			<view class="detail-hero" :style="heroStyle">
				<image
					v-if="heroUrl"
					class="detail-hero__img"
					:src="heroUrl"
					mode="aspectFill"
				/>
				<view v-else class="detail-hero__placeholder" />
			</view>

			<!-- 信息区 -->
			<view class="detail-body">
				<text class="detail-title">{{ title }}</text>

				<!-- 提示词卡(科技描边渐变) -->
				<view v-if="prompt" class="grad-card">
					<view class="grad-card__head">
						<text class="grad-card__label">提示词</text>
						<view class="grad-card__copy" @tap="copyPrompt">
							<text class="grad-card__copy-text">复制</text>
						</view>
					</view>
					<text class="grad-card__prompt am-selectable-text" user-select>{{ prompt }}</text>
				</view>

				<!-- 参数预设卡 -->
				<view v-if="paramRows.length" class="grad-card">
					<text class="grad-card__label grad-card__label--block">参数预设</text>
					<view
						v-for="(row, i) in paramRows"
						:key="row.key"
						class="param-row"
						:class="{ 'param-row--divider': i > 0 }"
					>
						<text class="param-row__key">{{ row.key }}</text>
						<text class="param-row__val">{{ row.value }}</text>
					</view>
				</view>
			</view>
		</scroll-view>

		<!-- 底部 CTA:用此模板去生成 -->
		<view class="detail-cta">
			<view class="detail-cta__btn" @tap="onUse">
				<text class="detail-cta__text">用此模板去生成</text>
			</view>
		</view>
	</view>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
	statusBarHeight: { type: Number, default: 44 },
	// 已拼接 baseUrl 的大图地址(页面端用封面 coverUrl)
	heroUrl: { type: String, default: '' },
	title: { type: String, default: '' },
	// 标签:逗号分隔字符串(保留入参,本组件不再渲染)
	tags: { type: String, default: '' },
	prompt: { type: String, default: '' },
	// 参数预设:JSON 字符串({mode,aspectRatio,count} 形态)
	params: { type: String, default: '' },
})

const emit = defineEmits(['close', 'use'])

// 大图容器按画幅占位:aspect-ratio = params.aspectRatio(缺省 3:4),图加载前容器已有高度,避免文字先出/图来撑开的跳动
const heroStyle = computed(() => {
	const obj = parseParams(props.params)
	const m = String((obj && obj.aspectRatio) || '3:4').split(':')
	const w = Number(m[0])
	const h = Number(m[1])
	const ratio = w > 0 && h > 0 ? `${w} / ${h}` : '3 / 4'
	return `aspect-ratio: ${ratio};`
})

// 模式中文映射
const MODE_LABEL = {
	text2img: '文生图',
	img2img: '图生图',
}

// 解析参数 JSON,生成展示行;容错:解析失败返回空
const paramRows = computed(() => {
	const obj = parseParams(props.params)
	if (!obj) return []
	const rows = []
	if (obj.mode) {
		rows.push({ key: '类型', value: MODE_LABEL[obj.mode] || obj.mode })
	}
	if (obj.aspectRatio) {
		rows.push({ key: '画幅', value: String(obj.aspectRatio) })
	}
	if (obj.count != null) {
		rows.push({ key: '数量', value: obj.count + ' 张' })
	}
	return rows
})

/** 安全解析参数 JSON,失败返回 null */
function parseParams(raw) {
	if (!raw) return null
	try {
		const obj = JSON.parse(raw)
		return obj && typeof obj === 'object' ? obj : null
	} catch (e) {
		return null
	}
}

/** 复制提示词到剪贴板 */
function copyPrompt() {
	if (!props.prompt) return
	uni.setClipboardData({
		data: props.prompt,
		success: () => {
			uni.showToast({ title: '已复制', icon: 'none' })
		},
	})
}

/** 点击 CTA:交由页面执行计数与带入逻辑 */
function onUse() {
	emit('use')
}
</script>

<style lang="scss" scoped>
.detail-overlay {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	bottom: 0;
	/* 高于底部导航 CustomTabBar(z-index:100),否则底部 CTA 被 tabbar 遮挡 */
	z-index: 200;
	background: #f6f7fb;
	display: flex;
	flex-direction: column;
}

/* 顶栏行:对齐胶囊安全区高度,容纳返回按钮 */
.detail-topbar {
	flex-shrink: 0;
	height: 88rpx;
	display: flex;
	align-items: center;
	padding-left: 28rpx;
}

.detail-scroll {
	/* flex 列里占满剩余空间;min-height:0 让 flex 子项可收缩,scroll-view 内部才能滚动 */
	flex: 1;
	min-height: 0;
}

/* 顶部大图:容器按画幅(:style aspect-ratio)固定占位,左右各 20rpx 留白 + 圆角 */
.detail-hero {
	position: relative;
	margin: 0 20rpx;
	border-radius: 28rpx;
	overflow: hidden;
	background: #dfeafa;
}

.detail-hero__img {
	width: 100%;
	height: 100%;
	/* aspectFill:填满按画幅占位的容器,加载完无高度变化、不跳动 */
	display: block;
}

.detail-hero__placeholder {
	width: 100%;
	height: 100%;
	background: repeating-linear-gradient(135deg, #e2ecf8 0 14rpx, #eef4fc 14rpx 28rpx);
}

/* 返回按钮 */
.detail-back {
	width: 72rpx;
	height: 72rpx;
	border-radius: 50%;
	background: rgba(255, 255, 255, 0.85);
	display: flex;
	align-items: center;
	justify-content: center;
}

.detail-back__arrow {
	width: 22rpx;
	height: 22rpx;
	border-left: 4rpx solid #4f84ea;
	border-bottom: 4rpx solid #4f84ea;
	transform: translateX(4rpx) rotate(45deg);
}

/* 信息区:左右各 20rpx,与大图对齐 */
.detail-body {
	padding: 24rpx 20rpx 40rpx;
}

.detail-title {
	display: block;
	font-size: 40rpx;
	font-weight: 700;
	color: #0f172a;
	line-height: 1.3;
}

/* 科技描边渐变卡:白底 + 渐变描边 */
.grad-card {
	margin-top: 28rpx;
	border-radius: 28rpx;
	padding: 28rpx 32rpx;
	background: linear-gradient(#fff, #fff) padding-box,
		linear-gradient(135deg, #7fb0f7, #a0c4fb) border-box;
	border: 3rpx solid transparent;
}

.grad-card__head {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 16rpx;
}

.grad-card__label {
	font-size: 24rpx;
	color: #94a3b8;
	line-height: 1;
}

.grad-card__label--block {
	display: block;
	margin-bottom: 24rpx;
}

.grad-card__copy {
	padding: 4rpx 8rpx;
}

.grad-card__copy-text {
	font-size: 24rpx;
	color: #4f84ea;
	line-height: 1;
}

.grad-card__prompt {
	display: block;
	font-size: 28rpx;
	line-height: 1.7;
	color: #475569;
}

/* 参数行 */
.param-row {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 16rpx 0;
}

.param-row--divider {
	border-top: 2rpx solid #eef2f8;
}

.param-row__key {
	font-size: 28rpx;
	color: #64748b;
}

.param-row__val {
	font-size: 28rpx;
	color: #1e293b;
}

/* 底部 CTA */
.detail-cta {
	flex-shrink: 0;
	padding: 24rpx 40rpx calc(40rpx + env(safe-area-inset-bottom));
	background: linear-gradient(180deg, rgba(246, 247, 251, 0), #f6f7fb 40%);
}

.detail-cta__btn {
	width: 100%;
	height: 96rpx;
	border-radius: 30rpx;
	background: linear-gradient(135deg, #4f84ea, #8fb4fb);
	display: flex;
	align-items: center;
	justify-content: center;
	box-shadow: 0 12rpx 32rpx rgba(96, 140, 224, 0.3);
}

.detail-cta__btn:active {
	opacity: 0.92;
}

.detail-cta__text {
	font-size: 32rpx;
	font-weight: 700;
	color: #ffffff;
}

/* 性能降级:去阴影 */
.am-performance-light .detail-cta__btn {
	box-shadow: none;
}
</style>
