<template>
	<!-- 图生图参考图上传:已传图带删除角标 + 虚线+号添加位 -->
	<view class="ref-uploader">
		<view class="ref-title">
			<text class="ref-title-text">参考图</text>
			<text class="ref-title-hint">（1-{{ max }} 张）</text>
			<text class="ref-order-hint" v-if="images.length > 1">长按排序</text>
		</view>
		<view class="ref-list">
			<!-- 已上传缩略图 -->
			<view class="ref-item" v-for="(url, index) in images" :key="url + index">
				<image
					class="ref-img"
					:src="resolveUrl(url)"
					mode="aspectFill"
					lazy-load
					@tap="preview(index)"
					@longpress.stop="openOrderEditor"
				/>
				<view class="ref-remove" @tap.stop="remove(index)">
					<text class="ref-remove-text">×</text>
				</view>
			</view>
			<!-- 虚线 + 号添加位:未满且未在上传时展示 -->
			<view class="ref-add" v-if="images.length < max" :class="{ 'ref-add--loading': uploading }" @tap="choose">
				<text v-if="!uploading" class="ref-add-plus">+</text>
				<text v-else class="ref-add-loading-text">上传中</text>
			</view>
		</view>

		<ImageOrderEditor
			:visible="orderEditorVisible"
			:urls="resolvedImages"
			@cancel="orderEditorVisible = false"
			@confirm="confirmOrder"
		/>
	</view>
</template>

<script setup>
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import config from '@/utils/config.js'
import { uploadFile } from '@/api/common.js'
import ImageOrderEditor from '@/components/common/ImageOrderEditor.vue'

const props = defineProps({
	// 已上传的图片相对路径列表(/profile/...)
	images: {
		type: Array,
		default: () => [],
	},
	// 最大张数
	max: {
		type: Number,
		default: 9,
	},
})

// 子组件不直接改 store,统一回调父组件维护 generationStore.refImages
const emit = defineEmits(['add', 'remove', 'reorder', 'uploading-change'])

// 上传中标记(防重复点)
const uploading = ref(false)
let uploadVersion = 0
const orderEditorVisible = ref(false)
const resolvedImages = computed(() => props.images.map((url) => resolveUrl(url)))

// 模板/作品恢复会替换整个参考图数组，旧上传不得追加到新输入。
watch(() => props.images, cancelUpload, { flush: 'sync' })
onBeforeUnmount(cancelUpload)

function setUploading(value) {
	uploading.value = value
	emit('uploading-change', value)
}

function cancelUpload() {
	uploadVersion += 1
	setUploading(false)
}

// 相对路径拼 baseUrl 供展示
function resolveUrl(url) {
	if (!url) return ''
	if (/^https?:\/\//.test(url)) return url
	return config.baseUrl + url
}

// 选图 → 逐张上传 → 收集 /profile url 回调父组件
function choose() {
	if (uploading.value) return
	const remain = props.max - props.images.length
	if (remain <= 0) return
	const version = ++uploadVersion
	setUploading(true)
	uni.chooseImage({
		count: remain,
		sizeType: ['compressed'],
		sourceType: ['album', 'camera'],
		success: async (res) => {
			const paths = res.tempFilePaths || []
			try {
				for (const p of paths) {
					if (version !== uploadVersion) return
					try {
						const data = await uploadFile(p, 'agent')
						if (version !== uploadVersion) return
						if (data && data.url) emit('add', data.url)
					} catch (e) {
						// 单张失败已由上传接口提示，后续图片继续按选择顺序上传。
					}
				}
			} finally {
				if (version === uploadVersion) setUploading(false)
			}
		},
		fail: () => {
			if (version === uploadVersion) setUploading(false)
		},
	})
}

// 删除指定索引参考图
function remove(index) {
	emit('remove', index)
}

function openOrderEditor() {
	if (uploading.value) {
		uni.showToast({ title: '参考图上传中，请稍候', icon: 'none' })
		return
	}
	if (props.images.length < 2) return
	orderEditorVisible.value = true
}

function confirmOrder(order) {
	emit('reorder', order)
	orderEditorVisible.value = false
}

// 预览已上传图
function preview(index) {
	uni.previewImage({
		current: index,
		urls: props.images.map((u) => resolveUrl(u)),
	})
}
</script>

<style lang="scss" scoped>
.ref-uploader {
	margin-top: 24rpx;
}

.ref-title {
	display: flex;
	align-items: baseline;
	margin-bottom: 20rpx;
}

.ref-title-text {
	font-size: 26rpx;
	color: #64748b;
	line-height: 1;
}

.ref-title-hint {
	margin-left: 4rpx;
	font-size: 26rpx;
	color: #4f84ea;
	line-height: 1;
}

.ref-order-hint {
	margin-left: auto;
	font-size: 22rpx;
	color: #94a3b8;
	line-height: 1;
}

.ref-list {
	display: flex;
	flex-wrap: wrap;
	gap: 20rpx;
}

.ref-item {
	position: relative;
	width: 168rpx;
	height: 168rpx;
	border-radius: 24rpx;
	overflow: hidden;
	border: 2rpx solid #dbe7f6;
}

.ref-img {
	width: 100%;
	height: 100%;
}

.ref-remove {
	position: absolute;
	top: 8rpx;
	right: 8rpx;
	width: 36rpx;
	height: 36rpx;
	border-radius: 50%;
	background: rgba(15, 23, 42, 0.5);
	display: flex;
	align-items: center;
	justify-content: center;
}

.ref-remove-text {
	font-size: 28rpx;
	color: #ffffff;
	line-height: 1;
}

.ref-add {
	width: 168rpx;
	height: 168rpx;
	box-sizing: border-box;
	border-radius: 24rpx;
	border: 2rpx dashed #c8c2ec;
	background: #f8f8fc;
	display: flex;
	align-items: center;
	justify-content: center;
	transition: opacity 0.15s ease, transform 0.15s ease;
}

.ref-add:active {
	opacity: 0.9;
	transform: scale(0.98);
}

.ref-add--loading {
	opacity: 0.7;
}

.ref-add-plus {
	font-size: 56rpx;
	font-weight: 300;
	color: #94a3b8;
	line-height: 1;
}

.ref-add-loading-text {
	font-size: 24rpx;
	color: #94a3b8;
	line-height: 1;
}
</style>
