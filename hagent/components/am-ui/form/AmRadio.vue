<template>
	<view class="am-radio" :class="{ 'am-radio--disabled': disabled }" @tap="handleTap">
		<view class="am-radio__dot" :class="{ 'am-radio__dot--checked': checked }">
			<view class="am-radio__inner" v-if="checked" />
		</view>
		<text class="am-radio__label" v-if="label">{{ label }}</text>
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


const props = defineProps({
	checked: {
		type: Boolean,
		default: false,
	},
	label: {
		type: String,
		default: '',
	},
	disabled: {
		type: Boolean,
		default: false,
	},
})

const emit = defineEmits(['tap'])

function handleTap(event) {
	if (props.disabled) return
	emit('tap', event)
}
</script>

<style lang="scss" scoped>
.am-radio {
	display: inline-flex;
	align-items: center;
	gap: 12rpx;
}

.am-radio--disabled {
	opacity: 0.45;
}

.am-radio__dot {
	width: 34rpx;
	height: 34rpx;
	border-radius: 50%;
	border: 2rpx solid #cbd5e1;
	background: #ffffff;
	display: flex;
	align-items: center;
	justify-content: center;
	box-sizing: border-box;
}

.am-radio__dot--checked {
	border-color: #2f6bff;
}

.am-radio__inner {
	width: 18rpx;
	height: 18rpx;
	border-radius: 50%;
	background: #2f6bff;
}

.am-radio__label {
	font-size: 26rpx;
	color: #334155;
	line-height: 36rpx;
}
</style>
