<template>
	<view class="am-countdown" :class="rootClass">
		<!-- 块状风格 -->
		<template v-if="variant === 'block'">
			<view class="am-countdown__block">
				<text class="am-countdown__digit">{{ display.hours }}</text>
			</view>
			<text class="am-countdown__separator">:</text>
			<view class="am-countdown__block">
				<text class="am-countdown__digit">{{ display.minutes }}</text>
			</view>
			<text class="am-countdown__separator">:</text>
			<view class="am-countdown__block">
				<text class="am-countdown__digit">{{ display.seconds }}</text>
			</view>
		</template>
		<!-- 内联风格 -->
		<template v-else>
			<text class="am-countdown__inline">{{ display.hours }}:{{ display.minutes }}:{{ display.seconds }}</text>
			<text class="am-countdown__suffix" v-if="suffix">{{ suffix }}</text>
		</template>
	</view>
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
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'

const props = defineProps({
	/** 目标时间戳（毫秒），优先级高于 duration */
	endTime: {
		type: Number,
		default: 0,
	},
	/** 倒计时总秒数 */
	duration: {
		type: Number,
		default: 0,
	},
	/** 风格：block 块状 / inline 内联 */
	variant: {
		type: String,
		default: 'block',
	},
	/** 尺寸：large / small */
	size: {
		type: String,
		default: 'large',
	},
	/** 内联风格后缀文字 */
	suffix: {
		type: String,
		default: '后结束',
	},
})

const emit = defineEmits(['finish'])

const remaining = ref(0)
let timer = null

/** 根据 props 计算剩余毫秒数 */
function calcRemaining() {
	if (props.endTime > 0) {
		return Math.max(0, props.endTime - Date.now())
	}
	return Math.max(0, props.duration * 1000)
}

/** 格式化为两位数字符串 */
function pad(n) {
	return n < 10 ? '0' + n : '' + n
}

const display = computed(() => {
	const total = Math.floor(remaining.value / 1000)
	const hours = Math.floor(total / 3600)
	const minutes = Math.floor((total % 3600) / 60)
	const seconds = total % 60
	return {
		hours: pad(hours),
		minutes: pad(minutes),
		seconds: pad(seconds),
	}
})

const rootClass = computed(() => [
	`am-countdown--${props.variant}`,
	`am-countdown--${props.size}`,
])

function startTimer() {
	stopTimer()
	remaining.value = calcRemaining()
	if (remaining.value <= 0) {
		emit('finish')
		return
	}
	timer = setInterval(() => {
		remaining.value = calcRemaining()
		if (remaining.value <= 0) {
			stopTimer()
			remaining.value = 0
			emit('finish')
		}
	}, 1000)
}

function stopTimer() {
	if (timer) {
		clearInterval(timer)
		timer = null
	}
}

onMounted(() => {
	startTimer()
})

onUnmounted(() => {
	stopTimer()
})

watch(() => [props.endTime, props.duration], () => {
	startTimer()
})
</script>

<style lang="scss" scoped>
.am-countdown {
	display: flex;
	align-items: center;
}

/* === 块状风格 === */
.am-countdown--block {
	gap: 8rpx;
}

.am-countdown__block {
	background: #1A1A2E;
	border-radius: 8rpx;
	display: flex;
	align-items: center;
	justify-content: center;
}

.am-countdown__digit {
	color: #ffffff;
	font-weight: 800;
	line-height: 1;
}

.am-countdown__separator {
	color: #1A1A2E;
	font-weight: 800;
	line-height: 1;
}

/* 块状-大号 */
.am-countdown--large .am-countdown__block {
	width: 48rpx;
	height: 52rpx;
}

.am-countdown--large .am-countdown__digit {
	font-size: 28rpx;
}

.am-countdown--large .am-countdown__separator {
	font-size: 24rpx;
}

/* 块状-小号 */
.am-countdown--small .am-countdown__block {
	width: 32rpx;
	height: 36rpx;
}

.am-countdown--small .am-countdown__digit {
	font-size: 20rpx;
}

.am-countdown--small .am-countdown__separator {
	font-size: 18rpx;
}

/* === 内联风格 === */
.am-countdown--inline {
	gap: 6rpx;
}

.am-countdown__inline {
	color: #FF4D4F;
	font-weight: 800;
	line-height: 1;
}

.am-countdown__suffix {
	color: var(--am-color-text-secondary);
	line-height: 1;
}

/* 内联-大号 */
.am-countdown--large .am-countdown__inline {
	font-size: 36rpx;
}

.am-countdown--large .am-countdown__suffix {
	font-size: 26rpx;
}

/* 内联-小号 */
.am-countdown--small .am-countdown__inline {
	font-size: 24rpx;
}

.am-countdown--small .am-countdown__suffix {
	font-size: 20rpx;
}
</style>
