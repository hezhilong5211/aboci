<template>
	<view class="am-search">
		<view class="am-search__box">
			<text class="am-search__icon">⌕</text>
			<input
				class="am-search__input"
				type="text"
				:value="modelValue"
				:placeholder="placeholder"
				@input="handleInput"
			/>
			<text class="am-search__clear" v-if="modelValue" @tap="emit('update:modelValue', '')">×</text>
		</view>
		<text class="am-search__cancel" v-if="showCancel" @tap="emit('cancel')">取消</text>
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


defineProps({
	modelValue: {
		type: String,
		default: '',
	},
	placeholder: {
		type: String,
		default: '搜索内容',
	},
	showCancel: {
		type: Boolean,
		default: false,
	},
})

const emit = defineEmits(['update:modelValue', 'input', 'cancel'])

function handleInput(event) {
	const value = event.detail.value
	emit('update:modelValue', value)
	emit('input', value)
}
</script>

<style lang="scss" scoped>
.am-search {
	display: flex;
	align-items: center;
	gap: 18rpx;
}

.am-search__box {
	flex: 1;
	height: 72rpx;
	border-radius: 18rpx;
	background: #f5f7fb;
	display: flex;
	align-items: center;
	padding: 0 22rpx;
	box-sizing: border-box;
}

.am-search__icon {
	font-size: 30rpx;
	color: #8c9ab0;
	margin-right: 12rpx;
}

.am-search__input {
	flex: 1;
	height: 100%;
	font-size: 26rpx;
	color: #1f2a37;
}

.am-search__clear {
	font-size: 30rpx;
	color: #9aa7b8;
	line-height: 1;
	padding-left: 12rpx;
}

.am-search__cancel {
	font-size: 26rpx;
	color: #2f6bff;
	line-height: 72rpx;
}
</style>
