<template>
	<!-- #ifndef APP-PLUS -->
	<transition name="am-fade">
	<!-- #endif -->
		<view class="am-bottom-sheet" v-if="visible" @tap="handleMaskTap">
			<!-- #ifndef APP-PLUS -->
			<transition name="am-slide-up" appear>
			<!-- #endif -->
				<view class="am-bottom-sheet__panel" @tap.stop>
					<view class="am-bottom-sheet__handle" />
					<view class="am-bottom-sheet__header" v-if="title || closeable">
						<text class="am-bottom-sheet__title">{{ title }}</text>
						<view class="am-bottom-sheet__close" v-if="closeable" @tap="close">
							<text class="am-bottom-sheet__close-text">×</text>
						</view>
					</view>
					<view class="am-bottom-sheet__body">
						<slot />
					</view>
					<view class="am-bottom-sheet__footer" v-if="$slots.footer">
						<slot name="footer" />
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
	closeable: {
		type: Boolean,
		default: true,
	},
	maskClosable: {
		type: Boolean,
		default: true,
	},
})

const emit = defineEmits(['update:visible', 'close'])

function close() {
	emit('update:visible', false)
	emit('close')
}

function handleMaskTap() {
	if (!props.maskClosable) return
	close()
}
</script>

<style lang="scss" scoped>
.am-bottom-sheet {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	bottom: 0;
	z-index: 1000;
	background: var(--am-color-bg-mask);
	transform: translateZ(0);
}

.am-bottom-sheet__panel {
	position: absolute;
	left: 0;
	right: 0;
	bottom: 0;
	padding: 18rpx 28rpx calc(28rpx + env(safe-area-inset-bottom));
	border-radius: 32rpx 32rpx 0 0;
	background: #ffffff;
	box-sizing: border-box;
	box-shadow: 0 -12rpx 34rpx rgba(15, 23, 42, 0.08);
}

.am-bottom-sheet__handle {
	width: 72rpx;
	height: 8rpx;
	border-radius: 999rpx;
	background: var(--am-color-border);
	margin: 0 auto 20rpx;
}

.am-bottom-sheet__header {
	min-height: 58rpx;
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 20rpx;
}

.am-bottom-sheet__title {
	font-size: 30rpx;
	color: var(--am-color-text-title);
	font-weight: 700;
	line-height: 40rpx;
}

.am-bottom-sheet__close {
	width: 56rpx;
	height: 56rpx;
	border-radius: 50%;
	background: var(--am-color-bg-muted);
	display: flex;
	align-items: center;
	justify-content: center;
}

.am-bottom-sheet__close-text {
	font-size: 34rpx;
	color: var(--am-color-text-secondary);
	line-height: 1;
}

.am-bottom-sheet__body {
	margin-top: 18rpx;
}

.am-bottom-sheet__footer {
	margin-top: 28rpx;
}

.am-performance-light .am-bottom-sheet__panel {
	box-shadow: none;
}

/* #ifdef APP-PLUS */
/* APP 端进入动画（替代 transition，避免 iOS 事件失效） */
.am-bottom-sheet {
	animation: am-overlay-in 0.2s ease;
}

.am-bottom-sheet__panel {
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

.am-performance-light .am-bottom-sheet,
.am-performance-light .am-bottom-sheet__panel,
.am-motion-none .am-bottom-sheet,
.am-motion-none .am-bottom-sheet__panel {
	animation: none;
}
/* #endif */
</style>
