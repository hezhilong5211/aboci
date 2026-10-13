<template>
	<view class="am-upload" :class="{ 'am-upload--disabled': disabled }">
		<view class="am-upload__list">
			<view class="am-upload__item" v-for="(item, index) in modelValue" :key="item.url || item.path || index">
				<image class="am-upload__image" :src="item.url || item.path || item" mode="aspectFill" />
				<view class="am-upload__remove" v-if="!disabled" @tap.stop="remove(index)">
					<text class="am-upload__remove-text">×</text>
				</view>
			</view>
			<view class="am-upload__add" v-if="canAdd" @tap="choose">
				<text class="am-upload__plus">＋</text>
				<text class="am-upload__text">{{ text }}</text>
			</view>
		</view>
		<text class="am-upload__tips" v-if="tips">{{ tips }}</text>
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


import { computed } from 'vue'

const props = defineProps({
	modelValue: {
		type: Array,
		default: () => [],
	},
	maxCount: {
		type: Number,
		default: 6,
	},
	text: {
		type: String,
		default: '上传',
	},
	tips: {
		type: String,
		default: '',
	},
	disabled: {
		type: Boolean,
		default: false,
	},
})

const emit = defineEmits(['update:modelValue', 'change', 'remove', 'choose'])
const canAdd = computed(() => !props.disabled && props.modelValue.length < props.maxCount)

function normalizeFiles(files) {
	return files.map((file) => ({
		path: file.path || file.tempFilePath || file,
		size: file.size || 0,
	}))
}

function choose() {
	if (!canAdd.value) return
	uni.chooseImage({
		count: props.maxCount - props.modelValue.length,
		sizeType: ['compressed'],
		sourceType: ['album', 'camera'],
		success: (res) => {
			const files = normalizeFiles(res.tempFiles || res.tempFilePaths || [])
			const next = props.modelValue.concat(files).slice(0, props.maxCount)
			emit('update:modelValue', next)
			emit('change', next)
			emit('choose', files)
		},
	})
}

function remove(index) {
	const next = props.modelValue.slice()
	const removed = next.splice(index, 1)[0]
	emit('update:modelValue', next)
	emit('change', next)
	emit('remove', removed, index)
}
</script>

<style lang="scss" scoped>
.am-upload {
	width: 100%;
}

.am-upload--disabled {
	opacity: 0.48;
}

.am-upload__list {
	display: flex;
	align-items: center;
	gap: 18rpx;
	flex-wrap: wrap;
}

.am-upload__item,
.am-upload__add {
	position: relative;
	width: 148rpx;
	height: 148rpx;
	border-radius: 18rpx;
	overflow: hidden;
}

.am-upload__item {
	background: var(--am-color-bg-muted);
}

.am-upload__image {
	width: 100%;
	height: 100%;
}

.am-upload__remove {
	position: absolute;
	top: 8rpx;
	right: 8rpx;
	width: 34rpx;
	height: 34rpx;
	border-radius: 50%;
	background: rgba(15, 23, 42, 0.58);
	display: flex;
	align-items: center;
	justify-content: center;
}

.am-upload__remove-text {
	font-size: 28rpx;
	color: #ffffff;
	line-height: 1;
}

.am-upload__add {
	box-sizing: border-box;
	border: 2rpx dashed var(--am-color-border);
	background: var(--am-color-bg-soft);
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
}

.am-upload__plus {
	font-size: 34rpx;
	color: var(--am-color-primary);
	line-height: 1;
}

.am-upload__text {
	margin-top: 8rpx;
	font-size: 22rpx;
	color: var(--am-color-text-secondary);
	line-height: 30rpx;
}

.am-upload__tips {
	display: block;
	margin-top: 12rpx;
	font-size: 22rpx;
	color: var(--am-color-text-tertiary);
	line-height: 32rpx;
}
</style>
