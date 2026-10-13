<template>
	<!-- + 号自定义弹窗:点 + 在输入条上方弹出。对应设计屏 03 第 109-113 行的白卡小面板 -->
	<view class="plus-popup" v-if="visible">
		<!-- 透明遮罩:点击关闭 -->
		<view class="plus-mask" @tap="close" />

		<!-- 面板:白卡 + 渐变描边 + 阴影 -->
		<view class="plus-panel">
			<!-- 上传图片 -->
			<view class="plus-item" @tap="onUpload">
				<image class="plus-item-img" src="/static/图片.png" mode="aspectFit" />
				<text class="plus-item-text">上传图片</text>
			</view>

			<!-- 激活 Skill 入口:有 skill 时显示 -->
			<view
				v-if="skillName"
				class="plus-item plus-item--skill"
				:class="{ 'plus-item--active': skillActive }"
				@tap="onSkill"
			>
				<text class="plus-item-icon">✦</text>
				<text class="plus-item-text plus-item-text--skill">{{ skillName }}</text>
				<text v-if="skillActive" class="plus-item-badge">已激活</text>
			</view>

			<!-- 指向输入条 + 号的小三角 -->
			<view class="plus-arrow" />
		</view>
	</view>
</template>

<script setup>
const props = defineProps({
	// 是否显示
	visible: { type: Boolean, default: false },
	// 唯一 skill 名(空则不显示 skill 入口)
	skillName: { type: String, default: '' },
	// skill 是否已激活(高亮 + 已激活标)
	skillActive: { type: Boolean, default: false },
})

const emit = defineEmits(['close', 'upload', 'toggle-skill'])

function close() {
	emit('close')
}

// 上传图片:由父组件触发 chooseAndUpload
function onUpload() {
	emit('upload')
}

// 切换 skill 激活态
function onSkill() {
	emit('toggle-skill')
}
</script>

<style lang="scss" scoped>
.plus-popup {
	position: absolute;
	left: 0;
	bottom: 100%;
	z-index: 20;
}

/* 透明遮罩:全屏覆盖,点空白关闭 */
.plus-mask {
	position: fixed;
	left: 0;
	right: 0;
	top: 0;
	bottom: 0;
}

/* 面板:贴输入条上方,左侧对齐 + 号 */
.plus-panel {
	position: absolute;
	left: 0;
	bottom: 24rpx;
	width: 340rpx;
	display: flex;
	flex-direction: column;
	background: linear-gradient(#fff, #fff) padding-box,
		linear-gradient(135deg, #7fb0f7, #a0c4fb) border-box;
	border: 2rpx solid transparent;
	border-radius: 24rpx;
	box-shadow: 0 20rpx 60rpx rgba(20, 24, 40, 0.16);
	overflow: hidden;
}

.plus-item {
	display: flex;
	align-items: center;
	gap: 20rpx;
	padding: 26rpx 32rpx;
}

.plus-item:active {
	background: #f4f7fc;
}

/* skill 项与上传项之间的分隔 */
.plus-item--skill {
	border-top: 1rpx solid #f1f2f5;
}

/* skill 已激活:浅蓝底 + 左竖条 */
.plus-item--active {
	background: #deeafb;
	border-left: 4rpx solid #4f84ea;
}

.plus-item-icon {
	font-size: 30rpx;
	line-height: 1;
	flex-shrink: 0;
}

/* 上传图片 png 图标(替代原 emoji) */
.plus-item-img {
	width: 36rpx;
	height: 36rpx;
	flex-shrink: 0;
}

.plus-item-text {
	flex: 1;
	min-width: 0;
	font-size: 28rpx;
	color: #1e293b;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.plus-item-text--skill {
	color: #4f84ea;
	font-weight: 600;
}

.plus-item-badge {
	flex-shrink: 0;
	font-size: 22rpx;
	font-weight: 600;
	color: #4f84ea;
	background: #deeafb;
	border-radius: 12rpx;
	padding: 4rpx 14rpx;
	line-height: 1.4;
}

/* 小三角:指向下方输入条的 + 号 */
.plus-arrow {
	position: absolute;
	left: 36rpx;
	bottom: -7rpx;
	width: 14rpx;
	height: 14rpx;
	background: #fff;
	border-right: 1rpx solid #a0c4fb;
	border-bottom: 1rpx solid #a0c4fb;
	transform: rotate(45deg);
}
</style>
