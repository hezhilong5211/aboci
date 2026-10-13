<template>
	<view class="case-content" :class="pagePerformanceClass" :style="pageVars">
		<scroll-view
			class="page-scroll"
			scroll-y
			:enhanced="scrollEnhanced"
			:bounces="scrollBounces"
			:show-scrollbar="scrollShowScrollbar"
			:lower-threshold="scrollLowerThreshold"
			@scrolltolower="loadMore"
		>
			<view
				class="page-inner"
				:class="{ 'page-inner--native': nativeNavigation }"
				:style="contentStyle"
			>
				<!-- 首屏骨架(首页加载且无数据时) -->
				<view v-if="loading && !records.length" class="skeleton-wrap">
					<am-skeleton :rows="6" />
				</view>

				<!-- 空数据兜底(视频本期可能为空) -->
				<view v-else-if="!records.length" class="empty-wrap">
					<am-empty title="暂无内容" :desc="emptyDesc" />
				</view>

				<template v-else>
					<!-- 顶部精选轮播:banner 风格,每屏一张,空精选不渲染 -->
					<swiper
						v-if="featured.length"
						class="featured-swiper"
						:indicator-dots="true"
						indicator-color="rgba(255,255,255,0.5)"
						indicator-active-color="#ffffff"
						:autoplay="true"
						:interval="4000"
						:circular="true"
					>
						<swiper-item
							v-for="item in featured"
							:key="item.templateId"
							class="featured-item"
							@tap="openDetail(item)"
						>
							<image
								v-if="resolveUrl(item.coverUrl)"
								class="featured-cover"
								:src="resolveUrl(item.coverUrl)"
								mode="aspectFill"
							/>
							<view v-else class="featured-cover featured-cover--placeholder" />
							<!-- 视频精选:中央播放按钮 -->
							<view v-if="isVideo" class="featured-play">
								<view class="featured-play__icon" />
							</view>
						</swiper-item>
					</swiper>

					<!-- 双列瀑布流:两个独立纵向 flex 列,手动分配,卡片按各自比例(aspect-ratio)错落 -->
					<view class="masonry">
						<view class="masonry-col">
							<CaseCard
								v-for="card in leftColumn"
								:key="card.templateId"
								:cover="resolveUrl(card.coverUrl)"
								:title="card.title"
								:use-count="Number(card.useCount) || 0"
								:aspect-ratio="card._ratio"
								:is-video="isVideo"
								@tap="openDetail(card)"
							/>
						</view>
						<view class="masonry-col">
							<CaseCard
								v-for="card in rightColumn"
								:key="card.templateId"
								:cover="resolveUrl(card.coverUrl)"
								:title="card.title"
								:use-count="Number(card.useCount) || 0"
								:aspect-ratio="card._ratio"
								:is-video="isVideo"
								@tap="openDetail(card)"
							/>
						</view>
					</view>

					<!-- 加载更多状态 -->
					<view v-if="loading" class="more-tip">
						<am-loading />
					</view>
					<view v-else-if="!hasMore" class="more-tip">
						<text class="more-tip__text">没有更多了</text>
					</view>
				</template>
			</view>
		</scroll-view>

		<!-- 详情浮层 -->
		<CaseDetailOverlay
			v-if="detailVisible"
			:status-bar-height="resolvedOverlayStatusBarHeight"
			:hero-url="detailHeroUrl"
			:title="detail.title"
			:prompt="detail.prompt"
			:params="detail.params"
			@close="closeDetail"
			@use="openUseModal"
		/>

		<!-- 用此模板弹窗:改提示词/画幅/张数,图生图可改参考图 -->
		<UseTemplateModal
			v-model:visible="useModalVisible"
			:template="detail"
			@confirm="onUseConfirmed"
		/>
	</view>
</template>

<script setup>
import { computed, ref } from 'vue'
import config from '@/utils/config.js'
import { listTemplates, getTemplate, getFeaturedTemplates, getTemplateCategories } from '@/api/template.js'
import { usePerformanceMode } from '@/composables/usePerformanceMode.js'
import { usePageScroll } from '@/composables/usePageScroll.js'
import CaseCard from './components/CaseCard.vue'
import CaseDetailOverlay from './components/CaseDetailOverlay.vue'
import UseTemplateModal from './components/UseTemplateModal.vue'

const { pagePerformanceClass } = usePerformanceMode()
const { scrollEnhanced, scrollBounces, scrollShowScrollbar, scrollLowerThreshold, contentStyle } = usePageScroll({ tabbar: true })

const props = defineProps({
	statusBarHeight: { type: Number, default: 44 },
	overlayStatusBarHeight: { type: Number, default: undefined },
	nativeNavigation: { type: Boolean, default: false },
})
const emit = defineEmits(['navigation-change'])

const pageVars = computed(() => ({
	'--status-bar-height': `${props.statusBarHeight}px`,
}))
const resolvedOverlayStatusBarHeight = computed(() =>
	Number.isFinite(props.overlayStatusBarHeight)
		? props.overlayStatusBarHeight
		: props.statusBarHeight
)

// 视频案例暂未开放，页面固定展示图片；后端媒体类型字段保留，便于后续恢复视频入口。
const MEDIA_TYPE = 'image'
const isVideo = false
const emptyDesc = '稍后再来看看'
const industryCategories = ref([])
const activeIndustryId = ref(null)

function emitNavigationState() {
	emit('navigation-change', {
		tabs:
			industryCategories.value.length > 1
				? industryCategories.value.map((item) => ({
					label: item.name,
					key: item.categoryId,
					disabled: false,
				}))
				: [],
		activeKey: activeIndustryId.value,
	})
}

// 列表分页状态
const records = ref([])
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)
const loading = ref(false)
const hasMore = ref(true)

// 顶部精选轮播数据(独立接口,精选≤5,兜底最早5条,空数组则不渲染轮播)
const featured = ref([])

// 瀑布流卡片比例:优先用案例自身 params.aspectRatio;缺失则按列+序号取兜底序列(交错错落)
const RATIO_SEQ_LEFT = ['16:9', '3:4', '1:1', '9:16', '4:3']
const RATIO_SEQ_RIGHT = ['9:16', '4:3', '3:4', '1:1', '3:4']

// 从案例 params(JSON 串)解析 aspectRatio,失败返回空
function parseAspect(card) {
	try {
		const p = JSON.parse(card && card.params ? card.params : '{}')
		return p && p.aspectRatio ? p.aspectRatio : ''
	} catch (e) {
		return ''
	}
}

// 全部 records 按左右两列交替分流;每张附 _ratio(真实比例优先,兜底序列保证起始高度错开)
const leftColumn = computed(() =>
	records.value
		.filter((_, i) => i % 2 === 0)
		.map((c, idx) => ({ ...c, _ratio: parseAspect(c) || RATIO_SEQ_LEFT[idx % RATIO_SEQ_LEFT.length] }))
)
const rightColumn = computed(() =>
	records.value
		.filter((_, i) => i % 2 === 1)
		.map((c, idx) => ({ ...c, _ratio: parseAspect(c) || RATIO_SEQ_RIGHT[idx % RATIO_SEQ_RIGHT.length] }))
)

/**
 * 拼接图片可访问地址。
 * 后端返回 /profile/* 相对路径,需拼 baseUrl;已是 http 的原样返回。
 */
function resolveUrl(url) {
	if (!url) return ''
	return url.startsWith('http') ? url : config.baseUrl + url
}

/** 拉取一页案例并累加 */
async function fetchList() {
	if (loading.value || !hasMore.value) return
	loading.value = true
	try {
		const res = await listTemplates({
			category: MEDIA_TYPE,
			industryCategoryId: activeIndustryId.value,
			pageNum: pageNum.value,
			pageSize,
		})
		const list = (res && res.records) || []
		total.value = (res && res.total) || 0
		records.value = records.value.concat(list)
		hasMore.value = records.value.length < total.value
		if (hasMore.value) {
			pageNum.value += 1
		}
	} catch (e) {
		// 错误已由 request 统一提示,停止后续加载避免空循环
		hasMore.value = false
	} finally {
		loading.value = false
	}
}

/** 拉取顶部精选轮播数据(按当前 tab 分类;失败/空则置空数组,轮播不渲染) */
async function fetchFeatured() {
	try {
		const res = await getFeaturedTemplates({
			category: MEDIA_TYPE,
			industryCategoryId: activeIndustryId.value,
		})
		featured.value = Array.isArray(res) ? res : []
	} catch (e) {
		// 精选拉取失败不阻断主列表浏览
		featured.value = []
	}
}

/** 触底加载下一页 */
function loadMore() {
	fetchList()
}

function resetCases() {
	records.value = []
	pageNum.value = 1
	total.value = 0
	hasMore.value = true
	featured.value = []
	fetchFeatured()
	fetchList()
}

async function loadIndustryCategories() {
	try {
		const list = await getTemplateCategories(MEDIA_TYPE)
		industryCategories.value = Array.isArray(list) ? list : []
	} catch (e) {
		industryCategories.value = []
	}
	activeIndustryId.value = industryCategories.value.length
		? industryCategories.value[0].categoryId
		: null
	emitNavigationState()
	resetCases()
}

function switchIndustry(categoryId) {
	if (activeIndustryId.value === categoryId) return
	activeIndustryId.value = categoryId
	emitNavigationState()
	resetCases()
}

function selectNavigationTab(key) {
	const category = industryCategories.value.find(
		(item) => String(item.categoryId) === String(key)
	)
	if (category) switchIndustry(category.categoryId)
}

// ====== 详情浮层 ======
const detailVisible = ref(false)
const detail = ref({})

// 用此模板弹窗可见态
const useModalVisible = ref(false)

// 详情大图:只用封面 coverUrl(不再解析 resultMedia)
const detailHeroUrl = computed(() => {
	const d = detail.value
	if (!d) return ''
	return resolveUrl(d.coverUrl || '')
})

/** 打开详情:拉取完整详情后展示浮层 */
async function openDetail(card) {
	if (!card || card.templateId == null) return
	// 先用列表项兜底渲染,再用详情接口补全 prompt/params/referenceImages
	detail.value = { ...card }
	detailVisible.value = true
	try {
		const res = await getTemplate(card.templateId)
		if (res) detail.value = res
	} catch (e) {
		// 详情拉取失败:保留列表项兜底,不阻断浏览
	}
}

/** 关闭详情浮层 */
function closeDetail() {
	detailVisible.value = false
}

/** 点击详情 CTA:打开「用此模板」弹窗(参数编辑、计数与带入由弹窗内完成) */
function openUseModal() {
	const d = detail.value
	if (!d || d.templateId == null) return
	useModalVisible.value = true
}

/** 弹窗确认后:关闭详情浮层(弹窗已自行计数、带入并切到生成页) */
function onUseConfirmed() {
	detailVisible.value = false
}

// 先读取动态行业分类，再按零个/一个/多个分类的兜底规则加载内容。
loadIndustryCategories()

defineExpose({ selectNavigationTab })
</script>

<style lang="scss" scoped>
.case-content {
	height: 100%;
	background: #f6f7fb;
	position: relative;
	overflow: hidden;
}

/* 滚动区 */
.page-scroll {
	height: 100%;
	position: relative;
	z-index: 2;
	overscroll-behavior: none;
}

.page-inner {
	padding: calc(var(--status-bar-height) + 80rpx) 32rpx 0;
}

.page-inner--native {
	padding-top: 24rpx;
}

.skeleton-wrap,
.empty-wrap {
	padding-top: 48rpx;
}

/* 顶部精选轮播:banner 风格,固定高,每屏一张 */
.featured-swiper {
	width: 100%;
	height: 360rpx;
}

.featured-item {
	position: relative;
	width: 100%;
	height: 100%;
	border-radius: 32rpx;
	overflow: hidden;
}

.featured-cover {
	width: 100%;
	height: 100%;
	display: block;
}

.featured-cover--placeholder {
	/* 无封面兜底:条纹底 */
	background: repeating-linear-gradient(135deg, #e2ecf8 0 14rpx, #eef4fc 14rpx 28rpx);
}

/* 视频精选播放按钮 */
.featured-play {
	position: absolute;
	left: 50%;
	top: 50%;
	transform: translate(-50%, -50%);
	width: 104rpx;
	height: 104rpx;
	border-radius: 50%;
	background: rgba(255, 255, 255, 0.9);
	display: flex;
	align-items: center;
	justify-content: center;
}

.featured-play__icon {
	width: 0;
	height: 0;
	margin-left: 8rpx;
	border-style: solid;
	border-width: 20rpx 0 20rpx 32rpx;
	border-color: transparent transparent transparent #4f84ea;
}

/* 双列瀑布流:外层 flex 顶对齐,两个独立纵向 flex 列;与上方轮播留间隔 */
.masonry {
	display: flex;
	gap: 24rpx;
	align-items: flex-start;
	margin-top: 32rpx;
}

.masonry-col {
	flex: 1;
	min-width: 0;
	display: flex;
	flex-direction: column;
	gap: 24rpx;
}

/* 加载更多 */
.more-tip {
	display: flex;
	align-items: center;
	justify-content: center;
	padding: 28rpx 0 8rpx;
}

.more-tip__text {
	font-size: 22rpx;
	color: #94a3b8;
}
</style>
