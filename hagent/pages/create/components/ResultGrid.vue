<template>
	<!-- 生成结果区:统一 2 列 1:1 网格,点击预览看真实比例;每张带保存按钮 -->
	<view class="result-grid" v-if="urls.length">
		<view class="result-head">
			<text class="result-title">本次生成</text>
			<text class="result-keep">作品保留 7 天 · 30 天内的生成请前往「个人中心」查看</text>
		</view>

		<!-- 2 列等宽网格:文生图/图生图统一 1:1 裁剪展示 -->
		<view class="grid-2x2">
			<view class="grid-item" v-for="(item, index) in resolvedList" :key="`${taskId}-${item.seq}`">
				<view class="grid-cell">
					<CachedGeneratedImage
						class="grid-img"
						:src="item.previewFull"
						:fallback-src="item.full"
						:user-id="currentUserId"
						cache-type="preview"
						mode="aspectFill"
						lazy-load
						@tap="preview(index)"
					/>
					<view class="grid-save" :class="{ disabled: saving }" @tap.stop="save(item)">
						<text class="grid-save-icon">↓</text>
					</view>
					<text class="ai-image-label">AI生成</text>
				</view>
				<view class="grid-detail" @tap="openDetail(item)">
					<text class="grid-detail-text">详情</text>
				</view>
			</view>
		</view>

		<!-- 图生图额外:全部保存到相册 -->
		<view v-if="mode === 'img2img'" class="save-all" :class="{ disabled: saving }" @tap="saveAll">
			<text class="save-all-icon">↓</text>
			<text class="save-all-text">{{ saving ? '保存中…' : '全部保存到相册' }}</text>
		</view>

		<text class="result-foot">作品保留 7 天 · 30 天内的生成请前往个人中心查看</text>
	</view>
</template>

<script setup>
import { computed, ref } from 'vue'
import config from '@/utils/config.js'
import CachedGeneratedImage from '@/components/common/CachedGeneratedImage.vue'
import { previewGeneratedImages, resolveGeneratedImage } from '@/utils/generated-image-cache.js'
import { useUserStore } from '@/store/user.js'
import { getGenerationDownloadInfo } from '@/api/agent.js'
import { saveImageToPhotoAlbum } from '@/utils/photo-album.js'

const props = defineProps({
	// 结果图相对路径列表(/profile/...)
	urls: {
		type: Array,
		default: () => [],
	},
	// 预览图路径列表，缺失时按索引回退原图
	previewUrls: {
		type: Array,
		default: () => [],
	},
	// 生成模式:布局统一网格,img2img 额外显示「全部保存」按钮
	mode: {
		type: String,
		default: 'text2img',
	},
	taskId: {
		type: String,
		default: '',
	},
})

const emit = defineEmits(['detail'])
const userStore = useUserStore()
const currentUserId = computed(() => userStore.userInfo && userStore.userInfo.userId)
const saving = ref(false)

// 拼好完整地址的列表,保留原始 url 与索引(供保存/预览)
const resolvedList = computed(() =>
	props.urls.map((url, index) => ({
		url,
		full: resolveUrl(url),
		previewFull: resolveUrl(props.previewUrls[index] || url),
		index,
		seq: index + 1,
	})).filter((item) => !!item.url)
)

function openDetail(item) {
	if (!props.taskId) return
	emit('detail', { taskId: props.taskId, seq: item.seq })
}

// 相对路径拼 baseUrl
function resolveUrl(url) {
	if (!url) return ''
	if (/^https?:\/\//.test(url)) return url
	return config.baseUrl + url
}

// 预览:只缓存用户当前点开的原图，其余仍按远程地址供左右滑动。
function preview(index) {
	return previewGeneratedImages(
		resolvedList.value.map((it) => it.full),
		index,
		currentUserId.value
	)
}

// 保存单张:优先复用持久原图缓存，未命中时下载后再写入相册。
async function save(item) {
	if (saving.value) return
	saving.value = true
	try {
		let path
		uni.showLoading({ title: '保存中', mask: true })
		try {
			path = await localDownloadPath(item)
		} catch (error) {
			console.warn('[ResultGrid] download image failed', error)
			uni.showToast({ title: '图片下载失败，请稍后重试', icon: 'none' })
			return
		} finally {
			uni.hideLoading()
		}

		try {
			const saved = await saveImageToPhotoAlbum(path)
			if (saved) uni.showToast({ title: '已保存', icon: 'success' })
		} catch (error) {
			console.warn('[ResultGrid] save image failed', error)
			uni.showToast({ title: '保存失败，请稍后重试', icon: 'none' })
		}
	} finally {
		saving.value = false
	}
}

// 全部保存(图生图):逐张顺序保存
async function saveAll() {
	const items = resolvedList.value
	if (!items.length || saving.value) return
	saving.value = true
	let ok = 0
	let downloadFailures = 0
	let firstSaveBlocked = false
	try {
		for (let index = 0; index < items.length; index += 1) {
			let path
			uni.showLoading({ title: `保存中 ${index + 1}/${items.length}`, mask: true })
			try {
				path = await localDownloadPath(items[index])
			} catch (error) {
				downloadFailures += 1
				console.warn('[ResultGrid] batch download failed', error)
				continue
			} finally {
				uni.hideLoading()
			}

			let saved = false
			try {
				saved = await saveImageToPhotoAlbum(path, {
					checkPermission: ok === 0,
					silent: ok > 0,
				})
			} catch (error) {
				console.warn('[ResultGrid] batch save failed', error)
				if (ok === 0) uni.showToast({ title: '保存失败，请稍后重试', icon: 'none' })
			}
			if (!saved && ok === 0) {
				firstSaveBlocked = true
				break
			}
			if (saved) ok += 1
		}
		if (firstSaveBlocked) return
		if (ok === items.length) {
			uni.showToast({ title: '已全部保存', icon: 'success' })
		} else if (ok > 0) {
			uni.showToast({ title: `已保存 ${ok}/${items.length} 张`, icon: 'none' })
		} else if (downloadFailures > 0) {
			uni.showToast({ title: '图片下载失败，请稍后重试', icon: 'none' })
		} else {
			uni.showToast({ title: '保存失败，请稍后重试', icon: 'none' })
		}
	} finally {
		uni.hideLoading()
		saving.value = false
	}
}

function downloadOne(url) {
	return new Promise((resolve, reject) => {
		uni.downloadFile({
			url,
			success: (res) => (res.statusCode === 200 ? resolve(res) : reject(res)),
			fail: reject,
		})
	})
}

async function localOriginalPath(url) {
	const cached = await resolveGeneratedImage(url, {
		userId: currentUserId.value,
		type: 'original',
	})
	if (!/^https?:\/\//i.test(cached)) return cached
	const response = await downloadOne(cached)
	return response.tempFilePath
}

async function localDownloadPath(item) {
	if (!props.taskId || !item || !item.seq) throw new Error('缺少生成任务信息')
	const info = await getGenerationDownloadInfo(props.taskId, item.seq)
	const url = resolveUrl(info && info.url)
	if (!url) throw new Error('下载地址为空')
	if (info && info.watermarked) {
		const response = await downloadOne(url)
		return response.tempFilePath
	}
	return localOriginalPath(url)
}

</script>

<style lang="scss" scoped>
.result-grid {
	margin-top: 40rpx;
}

.result-head {
	margin-bottom: 24rpx;
}

.result-title {
	display: block;
	font-size: 28rpx;
	font-weight: 600;
	color: #0f172a;
	line-height: 1;
}

.result-keep {
	display: block;
	margin-top: 12rpx;
	font-size: 22rpx;
	color: #94a3b8;
	line-height: 1.4;
}

/* 2 列等宽网格 */
.grid-2x2 {
	display: grid;
	grid-template-columns: 1fr 1fr;
	gap: 20rpx;
}

.grid-cell {
	position: relative;
	aspect-ratio: 1;
	border-radius: 24rpx;
	overflow: hidden;
	border: 2rpx solid #cfe0f5;
	background: repeating-linear-gradient(135deg, #e2ecf8 0 14rpx, #eef4fc 14rpx 28rpx);
}

.grid-item {
	min-width: 0;
}

.grid-detail {
	height: 56rpx;
	margin-top: 10rpx;
	border-radius: 18rpx;
	background: #eef3fb;
	display: flex;
	align-items: center;
	justify-content: center;
}

.grid-detail-text {
	font-size: 24rpx;
	font-weight: 600;
	color: #4f84ea;
}

.grid-img {
	width: 100%;
	height: 100%;
}

.grid-save {
	position: absolute;
	right: 14rpx;
	top: 14rpx;
	width: 56rpx;
	height: 56rpx;
	border-radius: 16rpx;
	background: rgba(255, 255, 255, 0.85);
	display: flex;
	align-items: center;
	justify-content: center;
}

.disabled {
	opacity: .45;
	pointer-events: none;
}

.ai-image-label {
	position: absolute;
	right: 14rpx;
	bottom: 14rpx;
	padding: 6rpx 12rpx;
	border-radius: 10rpx;
	background: rgba(15, 23, 42, 0.72);
	font-size: 20rpx;
	font-weight: 700;
	color: #ffffff;
	line-height: 1.2;
}

.grid-save-icon {
	font-size: 30rpx;
	color: #4f84ea;
	font-weight: 700;
	line-height: 1;
}

/* 全部保存按钮 */
.save-all {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 12rpx;
	height: 92rpx;
	margin-top: 28rpx;
	border-radius: 26rpx;
	border: 2rpx solid rgba(96, 140, 224, 0.3);
	background: #deeafb;
}

.save-all:active {
	opacity: 0.92;
}

.save-all-icon {
	font-size: 30rpx;
	color: #4f84ea;
	font-weight: 700;
	line-height: 1;
}

.save-all-text {
	font-size: 28rpx;
	color: #4f84ea;
	font-weight: 600;
	line-height: 1;
}

.result-foot {
	display: block;
	margin-top: 24rpx;
	font-size: 22rpx;
	color: #94a3b8;
	line-height: 1.4;
}
</style>
