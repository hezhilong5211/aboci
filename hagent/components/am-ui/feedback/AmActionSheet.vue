<template>
	<!-- #ifndef APP-PLUS -->
	<transition name="am-fade">
	<!-- #endif -->
		<view class="am-action-sheet" v-if="visible" @tap="close">
			<!-- #ifndef APP-PLUS -->
			<transition name="am-slide-up" appear>
			<!-- #endif -->
				<view class="am-action-sheet__panel" @tap.stop>
					<view class="am-action-sheet__header" v-if="title || desc">
						<text class="am-action-sheet__title" v-if="title">{{ title }}</text>
						<text class="am-action-sheet__desc" v-if="desc">{{ desc }}</text>
					</view>
					<view class="am-action-sheet__list">
						<view
							class="am-action-sheet__item"
							:class="[
								`am-action-sheet__item--${item.type || 'default'}`,
								{ 'am-action-sheet__item--disabled': item.disabled },
							]"
							v-for="item in actions"
							:key="item.value || item.label"
							@tap="select(item)"
						>
							<text class="am-action-sheet__item-text">{{ item.label }}</text>
						</view>
					</view>
					<view class="am-action-sheet__cancel" v-if="showCancel" @tap="close">
						<text class="am-action-sheet__cancel-text">{{ cancelText }}</text>
					</view>
				</view>
			<!-- #ifndef APP-PLUS -->
			</transition>
			<!-- #endif -->
		</view>
	<!-- #ifndef APP-PLUS -->
	</transition>
	<!-- #endif -->
</template>

<script>
export default {
	options: {
		styleIsolation: 'shared',
		virtualHost: true
	}
}
</script>

<script setup>


const props = defineProps({
	visible: {
		type: Boolean,
		default: false,
	},
	title: {
		type: String,
		default: '',
	},
	desc: {
		type: String,
		default: '',
	},
	actions: {
		type: Array,
		default: () => [],
	},
	showCancel: {
		type: Boolean,
		default: true,
	},
	cancelText: {
		type: String,
		default: '取消',
	},
	closeOnAction: {
		type: Boolean,
		default: true,
	},
})

const emit = defineEmits(['update:visible', 'close', 'select'])

function close() {
	emit('update:visible', false)
	emit('close')
}

function select(item) {
	if (item.disabled) return
	emit('select', item)
	if (props.closeOnAction) {
		close()
	}
}
</script>

<style lang="scss" scoped>
.am-action-sheet {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	bottom: 0;
	z-index: 1000;
	background: var(--am-color-bg-mask);
	transform: translateZ(0);
}

.am-action-sheet__panel {
	position: absolute;
	left: 0;
	right: 0;
	bottom: 0;
	padding: 18rpx 20rpx calc(20rpx + env(safe-area-inset-bottom));
	background: var(--am-color-bg-page);
	border-radius: 32rpx 32rpx 0 0;
	box-sizing: border-box;
}

.am-action-sheet__header,
.am-action-sheet__list,
.am-action-sheet__cancel {
	border-radius: 22rpx;
	background: #ffffff;
	overflow: hidden;
}

.am-action-sheet__header {
	padding: 26rpx 28rpx;
	text-align: center;
}

.am-action-sheet__title,
.am-action-sheet__desc {
	display: block;
}

.am-action-sheet__title {
	font-size: 28rpx;
	color: var(--am-color-text-title);
	font-weight: 700;
	line-height: 38rpx;
}

.am-action-sheet__desc {
	margin-top: 6rpx;
	font-size: 22rpx;
	color: var(--am-color-text-secondary);
	line-height: 32rpx;
}

.am-action-sheet__list {
	margin-top: 16rpx;
}

.am-action-sheet__item,
.am-action-sheet__cancel {
	height: 92rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	border-bottom: 1rpx solid var(--am-color-border-light);
}

.am-action-sheet__item:last-child {
	border-bottom: none;
}

.am-action-sheet__item--disabled {
	opacity: 0.42;
}

.am-action-sheet__item-text,
.am-action-sheet__cancel-text {
	font-size: 28rpx;
	line-height: 38rpx;
}

.am-action-sheet__item-text {
	color: var(--am-color-text-title);
}

.am-action-sheet__item--primary .am-action-sheet__item-text {
	color: var(--am-color-primary);
	font-weight: 600;
}

.am-action-sheet__item--danger .am-action-sheet__item-text {
	color: var(--am-color-danger);
}

.am-action-sheet__cancel {
	margin-top: 16rpx;
	border-bottom: none;
}

.am-action-sheet__cancel-text {
	color: var(--am-color-text-secondary);
	font-weight: 600;
}

/* #ifdef APP-PLUS */
/* APP 端进入动画（替代 transition，避免 iOS 事件失效） */
.am-action-sheet {
	animation: am-overlay-in 0.2s ease;
}

.am-action-sheet__panel {
	animation: am-panel-up 0.22s ease;
}

@keyframes am-overlay-in {
	from { opacity: 0; }
	to { opacity: 1; }
}

@keyframes am-panel-up {
	from {
		opacity: 0;
		transform: translateY(32rpx);
	}
	to {
		opacity: 1;
		transform: translateY(0);
	}
}

.am-performance-light .am-action-sheet,
.am-performance-light .am-action-sheet__panel,
.am-motion-none .am-action-sheet,
.am-motion-none .am-action-sheet__panel {
	animation: none;
}
/* #endif */
</style>
