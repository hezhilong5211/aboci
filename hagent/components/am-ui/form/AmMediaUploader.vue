<template>
	<view class="am-media-uploader" :class="{ 'am-media-uploader--disabled': disabled }">
		<view class="am-media-uploader__list">
			<view class="am-media-uploader__add" v-if="canAdd" @tap="choose">
				<text class="am-media-uploader__plus">＋</text>
				<text class="am-media-uploader__add-text">{{ addText }}</text>
			</view>

			<view
				class="am-media-uploader__item"
				v-for="(item, index) in visibleFiles"
				:key="item.url || item.path || index"
				@tap="preview(index)"
			>
				<image
					class="am-media-uploader__image"
					v-if="isImage(item)"
					:src="item.url || item.path || item"
					mode="aspectFill"
				/>
				<view class="am-media-uploader__video" v-else>
					<image
						class="am-media-uploader__image"
						v-if="item.thumb || item.thumbTempFilePath"
						:src="item.thumb || item.thumbTempFilePath"
						mode="aspectFill"
					/>
					<text class="am-media-uploader__video-icon">▶</text>
				</view>
				<view class="am-media-uploader__more" v-if="isLastVisible(index) && hiddenCount > 0">
					<text class="am-media-uploader__more-text">+{{ hiddenCount }}</text>
				</view>
				<view class="am-media-uploader__remove" v-else-if="!disabled" @tap.stop="remove(index)">
					<text class="am-media-uploader__remove-text">×</text>
				</view>
			</view>
		</view>
		<text class="am-media-uploader__tips" v-if="tips">{{ tips }}</text>
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
		default: 9,
	},
	maxVisible: {
		type: Number,
		default: 5,
	},
	maxVideoDuration: {
		type: Number,
		default: 60,
	},
	accept: {
		type: Array,
		default: () => ['image', 'video'],
	},
	addText: {
		type: String,
		default: '添加图片/视频',
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

const emit = defineEmits(['update:modelValue', 'change', 'remove', 'choose', 'preview'])
const canAdd = computed(() => !props.disabled && props.modelValue.length < props.maxCount)
const visibleLimit = computed(() => Math.max(0, props.maxVisible - (canAdd.value ? 1 : 0)))
const visibleFiles = computed(() => props.modelValue.slice(0, visibleLimit.value))
const hiddenCount = computed(() => Math.max(0, props.modelValue.length - visibleFiles.value.length))

function isImage(item) {
	const type = item.type || item.fileType || ''
	const path = item.url || item.path || item || ''
	return !type || type === 'image' || /\.(png|jpe?g|webp|gif)$/i.test(path)
}

function isLastVisible(index) {
	return index === visibleFiles.value.length - 1
}

function normalizeFiles(files, fallbackType) {
	return files.map((file) => {
		const type = file.fileType || file.type || fallbackType || 'image'
		return {
			path: file.path || file.tempFilePath || file,
			size: file.size || 0,
			duration: file.duration || 0,
			type,
			thumb: file.thumbTempFilePath || file.thumb || '',
		}
	})
}

function appendFiles(files) {
	const validFiles = files.filter((file) => {
		if (file.type !== 'video') return true
		if (!file.duration || file.duration <= props.maxVideoDuration) return true
		uni.showToast({ title: `视频最长${props.maxVideoDuration}秒`, icon: 'none' })
		return false
	})
	if (!validFiles.length) return
	const next = props.modelValue.concat(validFiles).slice(0, props.maxCount)
	emit('update:modelValue', next)
	emit('change', next)
	emit('choose', validFiles)
}

function chooseImage() {
	uni.chooseImage({
		count: props.maxCount - props.modelValue.length,
		sizeType: ['compressed'],
		sourceType: ['album', 'camera'],
		success: (res) => {
			appendFiles(normalizeFiles(res.tempFiles || res.tempFilePaths || [], 'image'))
		},
	})
}

function chooseVideo() {
	uni.chooseVideo({
		sourceType: ['album', 'camera'],
		maxDuration: props.maxVideoDuration,
		success: (res) => {
			appendFiles(normalizeFiles([res], 'video'))
		},
	})
}

function chooseMedia() {
	if (typeof uni.chooseMedia !== 'function') {
		showChooseSheet()
		return
	}
	uni.chooseMedia({
		count: props.maxCount - props.modelValue.length,
		mediaType: props.accept,
		sourceType: ['album', 'camera'],
		maxDuration: props.maxVideoDuration,
		sizeType: ['compressed'],
		success: (res) => {
			appendFiles(normalizeFiles(res.tempFiles || [], 'image'))
		},
	})
}

function showChooseSheet() {
	const itemList = props.accept.includes('video') ? ['图片', '视频'] : ['图片']
	uni.showActionSheet({
		itemList,
		success: (res) => {
			if (itemList[res.tapIndex] === '视频') {
				chooseVideo()
				return
			}
			chooseImage()
		},
	})
}

function choose() {
	if (!canAdd.value) return
	if (props.accept.includes('image') && props.accept.includes('video')) {
		chooseMedia()
		return
	}
	if (props.accept.includes('video')) {
		chooseVideo()
		return
	}
	chooseImage()
}

function remove(index) {
	const next = props.modelValue.slice()
	const removed = next.splice(index, 1)[0]
	emit('update:modelValue', next)
	emit('change', next)
	emit('remove', removed, index)
}

function preview(index) {
	emit('preview', visibleFiles.value[index], index)
}
</script>

<style lang="scss" scoped>
.am-media-uploader {
	width: 100%;
}

.am-media-uploader--disabled {
	opacity: 0.48;
}

.am-media-uploader__list {
	display: flex;
	align-items: center;
	gap: 14rpx;
	overflow: hidden;
}

.am-media-uploader__add,
.am-media-uploader__item {
	position: relative;
	width: 136rpx;
	height: 136rpx;
	border-radius: 14rpx;
	overflow: hidden;
	flex: 0 0 136rpx;
}

.am-media-uploader__add {
	box-sizing: border-box;
	border: 2rpx dashed rgba(47, 107, 255, 0.34);
	background: #f3f7ff;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
}

.am-media-uploader__plus {
	font-size: 48rpx;
	color: var(--am-color-primary);
	line-height: 48rpx;
}

.am-media-uploader__add-text {
	margin-top: 10rpx;
	font-size: 18rpx;
	color: var(--am-color-text-secondary);
	line-height: 26rpx;
	white-space: nowrap;
}

.am-media-uploader__item {
	background: var(--am-color-bg-muted);
}

.am-media-uploader__image,
.am-media-uploader__video {
	width: 100%;
	height: 100%;
}

.am-media-uploader__video {
	display: flex;
	align-items: center;
	justify-content: center;
	background: linear-gradient(135deg, #d8e1ef 0%, #aab7c8 100%);
}

.am-media-uploader__video-icon {
	position: absolute;
	font-size: 34rpx;
	color: #ffffff;
	line-height: 1;
	text-shadow: 0 2rpx 8rpx rgba(15, 23, 42, 0.28);
}

.am-media-uploader__remove {
	position: absolute;
	top: 8rpx;
	right: 8rpx;
	width: 34rpx;
	height: 34rpx;
	border-radius: 50%;
	background: rgba(31, 42, 55, 0.62);
	display: flex;
	align-items: center;
	justify-content: center;
}

.am-media-uploader__remove-text {
	font-size: 28rpx;
	color: #ffffff;
	line-height: 1;
}

.am-media-uploader__more {
	position: absolute;
	top: 0;
	right: 0;
	bottom: 0;
	left: 0;
	background: rgba(31, 42, 55, 0.34);
	backdrop-filter: blur(8rpx);
	display: flex;
	align-items: center;
	justify-content: center;
}

.am-media-uploader__more-text {
	font-size: 36rpx;
	color: #ffffff;
	font-weight: 800;
	line-height: 1;
}

.am-media-uploader__tips {
	display: block;
	margin-top: 22rpx;
	font-size: 24rpx;
	color: var(--am-color-text-tertiary);
	line-height: 34rpx;
}

.am-performance-light .am-media-uploader__more {
	backdrop-filter: none;
}
</style>
