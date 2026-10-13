<template>
	<!-- Agent 完成态分段卡:对应设计屏 08。有什么渲染什么,不写死段数 -->
	<view class="result-card">
		<!-- 顶部「已为你生成」提示(有任意内容时显示);标题不在此展示,统一走下方带复制的标题卡 -->
		<view class="result-lead" v-if="hasAny">
			<text class="result-lead-text">已为你生成 👇</text>
			<text class="result-ai-label">AI生成内容</text>
		</view>
		<view class="result-counts" v-if="images.length">
			<text class="result-count result-count--success">成功 {{ statusCounts.success }}</text>
			<text class="result-count result-count--running">生成中 {{ statusCounts.running }}</text>
			<text class="result-count result-count--failed">失败 {{ statusCounts.failed }}</text>
		</view>

		<!-- 图片组:每排 3 张，最多 9 张；点击预览，角标加入下一轮参考图 -->
		<view class="result-images" v-if="images.length">
			<view
				class="result-img"
				v-for="(img, idx) in images"
				:key="img.taskId + '-' + img.seq"
			>
				<!-- 成功:展示图片 -->
				<CachedGeneratedImage
					v-if="img.status === 'SUCCEEDED' && img.fullUrl"
					class="result-img-el"
					:src="img.previewUrl || img.fullUrl"
					:fallback-src="img.fullUrl"
					:user-id="currentUserId"
					cache-type="preview"
					mode="aspectFill"
					lazy-load
					@tap="preview(idx)"
					@longpress="saveImage(img)"
				/>
				<!-- 失败/超时:失败占位 -->
					<view v-else-if="img.status === 'FAILED'" class="result-img-failed">
						<text class="result-img-failed-text">生成失败</text>
						<view class="result-img-retry" :class="{ 'result-img-retry--disabled': img.retrying }" @tap.stop="retry(img, idx)">
							<text class="result-img-retry-text">{{ img.retrying ? '重试中' : '重试' }}</text>
						</view>
				</view>
				<!-- 进行中:条纹占位 + 进度 -->
				<view v-else class="result-img-loading">
					<text class="result-img-loading-text">生成中</text>
				</view>
					<view
					v-if="img.status === 'SUCCEEDED' && img.fullUrl"
					class="result-img-reference"
					@tap.stop="addReference(img)"
					@longpress.stop
				>
						<text class="result-img-reference-text">＋参考</text>
					</view>
					<text class="result-img-index">图{{ idx + 1 }}</text>
					<text v-if="img.status === 'SUCCEEDED' && img.fullUrl" class="ai-image-label">AI生成</text>
				</view>
		</view>

		<!-- 标题卡:有标题就显示(原「须有图才显」为出图套餐旧条件,纯文案场景会吞掉标题) -->
		<view class="result-section" v-if="result.title">
			<view class="result-section-head">
				<text class="result-section-label">标题</text>
				<view class="result-copy" @tap="copy(result.title)">
					<text class="result-copy-text">复制</text>
				</view>
			</view>
			<text class="result-section-title am-selectable-text" user-select>{{ result.title }}</text>
		</view>

		<!-- 文案卡 -->
		<view class="result-section" v-if="result.body">
			<view class="result-section-head">
				<text class="result-section-label">文案</text>
				<view class="result-copy" @tap="copy(result.body)">
					<text class="result-copy-text">复制</text>
				</view>
			</view>
			<text class="result-section-body am-selectable-text" user-select>{{ result.body }}</text>
		</view>

		<!-- 词条 chips -->
		<view class="result-section" v-if="tags.length">
			<view class="result-section-head">
				<text class="result-section-label">词条</text>
				<view class="result-copy" @tap="copy(tagsText)">
					<text class="result-copy-text">整组复制</text>
				</view>
			</view>
			<view class="result-tags">
				<view class="result-tag" v-for="(t, i) in tags" :key="i">
					<text class="result-tag-text am-selectable-text" user-select>{{ t }}</text>
				</view>
			</view>
		</view>
	</view>
</template>

<script setup>
import { computed, ref } from 'vue'
import CachedGeneratedImage from '@/components/common/CachedGeneratedImage.vue'
import { previewGeneratedImages, resolveGeneratedImage } from '@/utils/generated-image-cache.js'
import { useUserStore } from '@/store/user.js'
import config from '@/utils/config.js'
import { getGenerationDownloadInfo } from '@/api/agent.js'
import { saveImageToPhotoAlbum } from '@/utils/photo-album.js'

const props = defineProps({
	// AgentResult { title?, body?, tags?[], images?[{taskId,seq,status,fullUrl}] }
	result: { type: Object, required: true },
})
const emit = defineEmits(['add-reference', 'retry-image'])
const userStore = useUserStore()
const currentUserId = computed(() => userStore.userInfo && userStore.userInfo.userId)
const savingImage = ref(false)

// 图片列表(父组件轮询时回填 status/fullUrl)
const images = computed(() => (Array.isArray(props.result.images) ? props.result.images : []))
const statusCounts = computed(() => ({
	success: images.value.filter((img) => img.status === 'SUCCEEDED').length,
	running: images.value.filter((img) => img.status !== 'SUCCEEDED' && img.status !== 'FAILED').length,
	failed: images.value.filter((img) => img.status === 'FAILED').length,
}))

// 词条列表
const tags = computed(() => (Array.isArray(props.result.tags) ? props.result.tags : []))

// 词条整组文案(空格分隔,供整组复制)
const tagsText = computed(() => tags.value.join(' '))

// 是否有任意可展示内容
const hasAny = computed(
	() => images.value.length || props.result.title || props.result.body || tags.value.length
)

// 复制到剪贴板
function copy(text) {
	if (!text) return
	uni.setClipboardData({
		data: String(text),
		success: () => uni.showToast({ title: '已复制', icon: 'none' }),
	})
}

// 预览成功的图片
function preview(idx) {
	const urls = images.value
		.filter((img) => img.status === 'SUCCEEDED' && img.fullUrl)
		.map((img) => img.fullUrl)
	if (!urls.length) return
	const current = images.value[idx] && images.value[idx].fullUrl
	const currentIndex = Math.max(0, urls.indexOf(current))
	return previewGeneratedImages(urls, currentIndex, currentUserId.value)
}

function addReference(img) {
	if (!img || img.status !== 'SUCCEEDED' || !img.fullUrl) return
	emit('add-reference', img)
}

function retry(img, index) {
	if (!img || img.status !== 'FAILED' || img.retrying) return
	emit('retry-image', { image: img, index })
}

// 保存单张图片到相册:先下载临时文件,再写入相册
async function saveImage(img) {
	if (!img || !img.taskId || !img.seq || !img.fullUrl || savingImage.value) return
	savingImage.value = true
	try {
		let filePath
		uni.showLoading({ title: '保存中…', mask: true })
		try {
			const info = await getGenerationDownloadInfo(img.taskId, img.seq)
			const downloadUrl = resolveUrl(info && info.url)
			if (info && info.watermarked) {
				filePath = await downloadOne(downloadUrl)
			} else {
				const cached = await resolveGeneratedImage(downloadUrl, {
					userId: currentUserId.value,
					type: 'original',
				})
				filePath = /^https?:\/\//i.test(cached) ? await downloadOne(cached) : cached
			}
		} catch (error) {
			console.warn('[ResultCard] download image failed', error)
			uni.showToast({ title: '图片下载失败，请稍后重试', icon: 'none' })
			return
		} finally {
			uni.hideLoading()
		}

		try {
			const saved = await saveImageToPhotoAlbum(filePath)
			if (saved) uni.showToast({ title: '已保存到相册', icon: 'none' })
		} catch (error) {
			console.warn('[ResultCard] save image failed', error)
			uni.showToast({ title: '保存失败，请稍后重试', icon: 'none' })
		}
	} finally {
		savingImage.value = false
	}
}

function resolveUrl(url) {
	if (!url) return ''
	return /^https?:\/\//i.test(url) ? url : config.baseUrl + url
}

function downloadOne(url) {
	return new Promise((resolve, reject) => {
		uni.downloadFile({
			url,
			success: (res) => {
				if (res.statusCode === 200 && res.tempFilePath) resolve(res.tempFilePath)
				else reject(res)
			},
			fail: reject,
		})
	})
}

</script>

<style lang="scss" scoped>
.result-card {
	width: 100%;
	display: flex;
	flex-direction: column;
	gap: 20rpx;
}

.result-lead {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 16rpx;
}

.result-lead-text {
	font-size: 30rpx;
	font-weight: 600;
	color: #0f172a;
}

.result-ai-label {
	flex-shrink: 0;
	padding: 6rpx 14rpx;
	border-radius: 12rpx;
	background: #eaf2ff;
	font-size: 21rpx;
	font-weight: 700;
	color: #2563eb;
}

.result-counts {
	display: flex;
	gap: 12rpx;
	flex-wrap: wrap;
}

.result-count {
	padding: 6rpx 14rpx;
	border-radius: 999rpx;
	font-size: 21rpx;
}

.result-count--success {
	color: #15803d;
	background: #dcfce7;
}

.result-count--running {
	color: #2563eb;
	background: #dbeafe;
}

.result-count--failed {
	color: #dc2626;
	background: #fee2e2;
}

/* 图片组:每排 3 张等分卡宽 */
.result-images {
	display: grid;
	grid-template-columns: repeat(3, 1fr);
	gap: 12rpx;
}

.result-img {
	position: relative;
	aspect-ratio: 1;
	border-radius: 16rpx;
	overflow: hidden;
	border: 1rpx solid #dbe7f6;
}

.result-img-el {
	width: 100%;
	height: 100%;
}

.result-img-loading,
.result-img-failed {
	width: 100%;
	height: 100%;
	display: flex;
	align-items: center;
	justify-content: center;
	background: repeating-linear-gradient(
		135deg,
		#e2ecf8 0 14rpx,
		#eef4fc 14rpx 28rpx
	);
}

.result-img-loading-text {
	font-size: 22rpx;
	color: #4f84ea;
}

.result-img-failed {
	flex-direction: column;
	gap: 10rpx;
	background: #f4f7fc;
}

.result-img-failed-text {
	font-size: 24rpx;
	color: #ef4444;
}

.result-img-retry {
	height: 40rpx;
	padding: 0 16rpx;
	border-radius: 20rpx;
	background: #dbeafe;
	display: flex;
	align-items: center;
	justify-content: center;
}

.result-img-retry--disabled {
	opacity: 0.6;
}

.result-img-retry-text {
	font-size: 20rpx;
	font-weight: 600;
	color: #2563eb;
}

.result-img-index {
	position: absolute;
	left: 8rpx;
	top: 8rpx;
	padding: 4rpx 10rpx;
	border-radius: 999rpx;
	background: rgba(15, 23, 42, 0.68);
	font-size: 20rpx;
	font-weight: 600;
	color: #fff;
}

.ai-image-label {
	position: absolute;
	right: 8rpx;
	bottom: 8rpx;
	padding: 4rpx 10rpx;
	border-radius: 9rpx;
	background: rgba(15, 23, 42, 0.72);
	font-size: 18rpx;
	font-weight: 700;
	color: #ffffff;
	line-height: 1.2;
}

/* 添加为下一轮参考图的独立角标，事件不会触发图片预览 */
.result-img-reference {
	position: absolute;
	right: 8rpx;
	top: 8rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	height: 40rpx;
	padding: 0 12rpx;
	border-radius: 20rpx;
	background: rgba(15, 23, 42, 0.68);
}

.result-img-reference-text {
	font-size: 20rpx;
	font-weight: 600;
	color: #fff;
}

/* 分段卡(标题/文案/词条):科技描边 */
.result-section {
	padding: 24rpx 28rpx;
	border-radius: 24rpx;
	background: linear-gradient(#fff, #fff) padding-box,
		linear-gradient(135deg, #7fb0f7, #a0c4fb) border-box;
	border: 3rpx solid transparent;
}

.result-section-head {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 12rpx;
}

.result-section-label {
	font-size: 22rpx;
	color: #94a3b8;
}

.result-copy {
	display: flex;
	align-items: center;
	padding: 4rpx 8rpx;
}

.result-copy:active {
	opacity: 0.7;
}

.result-copy-text {
	font-size: 24rpx;
	color: #4f84ea;
}

.result-section-title {
	font-size: 28rpx;
	font-weight: 600;
	line-height: 1.5;
	color: #0f172a;
}

.result-section-body {
	font-size: 26rpx;
	line-height: 1.75;
	color: #475569;
	white-space: pre-wrap;
	word-break: break-word;
}

/* 词条 chips */
.result-tags {
	display: flex;
	flex-wrap: wrap;
	gap: 14rpx;
}

.result-tag {
	padding: 10rpx 22rpx;
	border-radius: 26rpx;
	background: #deeafb;
}

.result-tag-text {
	font-size: 24rpx;
	color: #4f84ea;
}
</style>
