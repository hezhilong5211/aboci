<template>
	<view class="am-mention-input">
		<textarea
			class="am-mention-input__textarea"
			:value="modelValue"
			:placeholder="placeholder"
			:maxlength="maxlength"
			@input="handleInput"
		></textarea>
		<view class="am-mention-input__footer">
			<view class="am-mention-input__chips">
				<AmTag v-for="item in mentions" :key="item" :text="`@${item}`" plain />
				<AmTag v-for="item in topics" :key="item" :text="`#${item}`" plain />
			</view>
			<text class="am-mention-input__count">{{ modelValue.length }}/{{ maxlength }}</text>
		</view>
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


import AmTag from '../base/AmTag.vue'

defineProps({
	modelValue: {
		type: String,
		default: '',
	},
	placeholder: {
		type: String,
		default: '分享你的想法...',
	},
	maxlength: {
		type: Number,
		default: 1000,
	},
	mentions: {
		type: Array,
		default: () => [],
	},
	topics: {
		type: Array,
		default: () => [],
	},
})

const emit = defineEmits(['update:modelValue'])

function handleInput(event) {
	emit('update:modelValue', event.detail.value)
}
</script>

<style lang="scss" scoped>
.am-mention-input {
	width: 100%;
}

.am-mention-input__textarea {
	width: 100%;
	min-height: 260rpx;
	font-size: 26rpx;
	color: var(--am-color-text-title);
	line-height: 42rpx;
	box-sizing: border-box;
}

.am-mention-input__footer {
	display: flex;
	align-items: flex-end;
	justify-content: space-between;
	gap: 18rpx;
	margin-top: 12rpx;
}

.am-mention-input__chips {
	display: flex;
	align-items: center;
	flex-wrap: wrap;
	gap: 10rpx;
}

.am-mention-input__count {
	font-size: 20rpx;
	color: var(--am-color-text-tertiary);
	line-height: 28rpx;
}
</style>
