<template>
	<!-- Skill 选择弹层:对应设计屏 05。复用 am-bottom-sheet 承载 -->
	<am-bottom-sheet :visible="visible" title="Skill 激活" @update:visible="onSheetVisible" @close="onClose">
		<view class="skill-list">
			<!-- 加载态 -->
			<view v-if="loading" class="skill-loading">
				<text class="skill-loading-text">加载中…</text>
			</view>

			<!-- 空态 -->
			<am-empty v-else-if="!skills.length" title="暂无可用 Skill" desc="稍后再试" />

			<!-- Skill 列表(v-for 与分支判断拆开:template 承载 v-else,内层 view 只做循环,避免 v-for/v-else 同元素冲突) -->
			<template v-else>
				<view
					class="skill-item"
					:class="{ 'skill-item--active': activeName === skill.name }"
					v-for="skill in skills"
					:key="skill.skillId"
					@tap="select(skill)"
				>
					<!-- 图标:有 icon 用图,无则渐变星兜底 -->
					<image v-if="skill.fullIcon" class="skill-icon-img" :src="skill.fullIcon" mode="aspectFill" />
					<view v-else class="skill-icon">
						<text class="skill-icon-glyph">✦</text>
					</view>
					<view class="skill-info">
						<text class="skill-name">{{ skill.name }}</text>
						<text class="skill-desc" v-if="skill.description">{{ skill.description }}</text>
					</view>
					<!-- 选中圆点 -->
					<view class="skill-radio" :class="{ 'skill-radio--on': activeName === skill.name }">
						<view class="skill-radio-dot" v-if="activeName === skill.name" />
					</view>
				</view>
			</template>
		</view>
	</am-bottom-sheet>
</template>

<script setup>
const props = defineProps({
	// 是否显示
	visible: { type: Boolean, default: false },
	// Skill 列表(已由父组件拼好 fullIcon 绝对地址)
	skills: { type: Array, default: () => [] },
	// 加载态
	loading: { type: Boolean, default: false },
	// 当前已激活的 Skill 名(高亮用)
	activeName: { type: String, default: '' },
})

const emit = defineEmits(['update:visible', 'select', 'close'])

// 选中一个 Skill:回传整项,父组件取 name 作为 chatStream skill 参数
function select(skill) {
	emit('select', skill)
}

function onSheetVisible(v) {
	emit('update:visible', v)
}

function onClose() {
	emit('close')
}
</script>

<style lang="scss" scoped>
.skill-list {
	display: flex;
	flex-direction: column;
	gap: 12rpx;
	max-height: 60vh;
	overflow: hidden;
}

.skill-loading {
	padding: 48rpx 0;
	text-align: center;
}

.skill-loading-text {
	font-size: 26rpx;
	color: #94a3b8;
}

.skill-item {
	display: flex;
	align-items: center;
	gap: 24rpx;
	padding: 24rpx 28rpx;
	border-radius: 24rpx;
	background: #fff;
	border: 1rpx solid #edeef2;
}

.skill-item:active {
	opacity: 0.92;
}

/* 选中:浅蓝底 + 左竖条 */
.skill-item--active {
	background: #deeafb;
	border-color: rgba(96, 140, 224, 0.2);
	border-left: 4rpx solid #4f84ea;
}

.skill-icon,
.skill-icon-img {
	width: 84rpx;
	height: 84rpx;
	border-radius: 24rpx;
	flex-shrink: 0;
}

.skill-icon {
	background: linear-gradient(135deg, #4f84ea, #8fb4fb);
	display: flex;
	align-items: center;
	justify-content: center;
}

.skill-icon-glyph {
	font-size: 40rpx;
	color: #fff;
	line-height: 1;
}

.skill-info {
	flex: 1;
	min-width: 0;
	display: flex;
	flex-direction: column;
	gap: 6rpx;
}

.skill-name {
	font-size: 30rpx;
	font-weight: 600;
	color: #0f172a;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.skill-desc {
	font-size: 24rpx;
	color: #64748b;
	line-height: 1.4;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

/* 单选圆点 */
.skill-radio {
	width: 40rpx;
	height: 40rpx;
	border-radius: 50%;
	border: 3rpx solid #e3e8f0;
	flex-shrink: 0;
	display: flex;
	align-items: center;
	justify-content: center;
}

.skill-radio--on {
	border-color: #4f84ea;
	background: #4f84ea;
}

.skill-radio-dot {
	width: 16rpx;
	height: 16rpx;
	border-radius: 50%;
	background: #fff;
}
</style>
