<template>
	<view class="shop-page" :class="pagePerformanceClass" @touchstart="handleTouchStart" @touchend="handleTouchEnd">
		<!-- 顶部导航栏：返回 + 搜索框 -->
		<view class="shop-nav" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="shop-nav-inner">
				<view class="shop-nav-back" @tap="goBack">
					<text class="shop-nav-back-text">‹</text>
				</view>
				<view class="shop-nav-search" @tap="showComingSoon('搜索')">
					<text class="shop-nav-search-icon">⌕</text>
					<text class="shop-nav-search-placeholder">搜索商品、品牌、品类</text>
				</view>
			</view>
		</view>

		<scroll-view class="shop-scroll" scroll-y :enhanced="scrollEnhanced" :bounces="scrollBounces" :show-scrollbar="scrollShowScrollbar" :lower-threshold="scrollLowerThreshold">
			<view class="shop-inner" :style="[contentStyle, { paddingTop: (statusBarHeight + 72) + 'px' }]">
				<!-- Banner 轮播 -->
				<view class="shop-banner">
					<AmCarousel :items="bannerItems" autoplay :interval="3600" />
				</view>

				<!-- 快捷分类入口 -->
				<AmCard class="shop-section-card" padding="26rpx">
					<AmGrid :columns="4" row-gap="24rpx">
						<view class="category-item" v-for="item in categoryEntries" :key="item.title" @tap="showComingSoon(item.title)">
							<AmIconBox :icon="item.icon" :background="item.bg" :shadow="false" size="88rpx" />
							<text class="category-title">{{ item.title }}</text>
						</view>
					</AmGrid>
				</AmCard>

				<!-- 限时秒杀 -->
				<AmCard class="shop-section-card" padding="26rpx">
					<AmSection title="限时秒杀">
						<template #extra>
							<AmCountdown :duration="seckillSeconds" variant="block" size="small" @finish="onSeckillFinish" />
						</template>
					</AmSection>
					<scroll-view class="seckill-scroll" scroll-x :show-scrollbar="false">
						<view class="seckill-list">
							<view class="seckill-item" v-for="item in seckillProducts" :key="item.title" @tap="showComingSoon(item.title)">
								<image class="seckill-image" :src="item.image" mode="aspectFill" />
								<text class="seckill-title">{{ item.title }}</text>
								<view class="seckill-price-row">
									<text class="seckill-price">¥{{ item.price }}</text>
									<text class="seckill-origin">¥{{ item.originPrice }}</text>
								</view>
							</view>
						</view>
					</scroll-view>
				</AmCard>

				<!-- 优惠券 -->
				<AmCard class="shop-section-card" padding="26rpx">
					<AmSection title="领券中心" more-text="更多" @more="showComingSoon('更多优惠券')">
					</AmSection>
					<view class="coupon-list">
						<AmCouponCard
							v-for="item in coupons"
							:key="item.title"
							:amount="item.amount"
							:title="item.title"
							:desc="item.desc"
							:time="item.time"
							@tap="showComingSoon('领取优惠券')"
						/>
					</view>
				</AmCard>

				<!-- 为你推荐（瀑布流） -->
				<AmCard class="shop-section-card shop-waterfall-card" padding="26rpx">
					<AmSection title="✦ 为你推荐" />
					<AmWaterfall gap="16rpx">
						<template #left>
							<view class="wf-card" v-for="item in leftProducts" :key="item.title" @tap="showComingSoon(item.title)">
								<view class="wf-cover">
									<image class="wf-image" :src="item.image" mode="widthFix" />
									<view class="wf-badge" v-if="item.badge">
										<text class="wf-badge-text">{{ item.badge }}</text>
									</view>
								</view>
								<view class="wf-info">
									<text class="wf-title">{{ item.title }}</text>
									<view class="wf-footer">
										<view class="wf-price-wrap">
											<text class="wf-price">¥{{ item.price }}</text>
											<text class="wf-origin" v-if="item.originPrice">¥{{ item.originPrice }}</text>
										</view>
										<view class="wf-cart">
											<text class="wf-cart-text">＋</text>
										</view>
									</view>
								</view>
							</view>
						</template>
						<template #right>
							<view class="wf-card" v-for="item in rightProducts" :key="item.title" @tap="showComingSoon(item.title)">
								<view class="wf-cover">
									<image class="wf-image" :src="item.image" mode="widthFix" />
									<view class="wf-badge" v-if="item.badge">
										<text class="wf-badge-text">{{ item.badge }}</text>
									</view>
								</view>
								<view class="wf-info">
									<text class="wf-title">{{ item.title }}</text>
									<view class="wf-footer">
										<view class="wf-price-wrap">
											<text class="wf-price">¥{{ item.price }}</text>
											<text class="wf-origin" v-if="item.originPrice">¥{{ item.originPrice }}</text>
										</view>
										<view class="wf-cart">
											<text class="wf-cart-text">＋</text>
										</view>
									</view>
								</view>
							</view>
						</template>
					</AmWaterfall>
				</AmCard>
			</view>
		</scroll-view>

		<AmModal :visible="showModal" :title="modalTitle" @close="showModal = false" />
	</view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { usePerformanceMode } from '@/composables/usePerformanceMode.js'
import { usePageScroll } from '@/composables/usePageScroll.js'
import { usePageGesture } from '@/composables/usePageGesture.js'

const { pagePerformanceClass } = usePerformanceMode()
const { scrollEnhanced, scrollBounces, scrollShowScrollbar, scrollLowerThreshold, contentStyle } = usePageScroll()
const { handleTouchStart, handleTouchEnd } = usePageGesture({ onBack: goBack })

const statusBarHeight = ref(44)
uni.getSystemInfo({ success: (info) => { statusBarHeight.value = info.statusBarHeight || 44 } })

const showModal = ref(false)
const modalTitle = ref('')

function showComingSoon(title) {
	modalTitle.value = title
	showModal.value = true
}

function goBack() {
	uni.navigateBack()
}

/* ====== 秒杀倒计时：2小时15分38秒 ====== */
const seckillSeconds = ref(2 * 3600 + 15 * 60 + 38)

function onSeckillFinish() {
	console.log('秒杀结束')
}

/* ====== Banner ====== */
const bannerItems = [
	{ image: '/static/shop/product-2.png', title: '新品首发 · 降噪耳机', desc: '头戴式无线蓝牙耳机 主动降噪 高音质长续航' },
	{ image: '/static/shop/product-6.png', title: '品质生活 · 全棉四件套', desc: '全棉四件套纯棉床上用品 ins风简约床单被套' },
	{ image: '/static/shop/product-3.png', title: '厨电狂欢 · 咖啡机', desc: '家用半自动咖啡机 意式浓缩奶泡一体机' },
]

/* ====== 快捷分类入口 ====== */
const categoryEntries = [
	{ icon: '👕', title: '服饰', bg: 'linear-gradient(135deg, #FFB86C 0%, #FF7A45 100%)' },
	{ icon: '📱', title: '数码', bg: 'linear-gradient(135deg, #72AEFF 0%, #4B8FFF 100%)' },
	{ icon: '🏠', title: '家居', bg: 'linear-gradient(135deg, #72E7C0 0%, #38D6A0 100%)' },
	{ icon: '🍳', title: '厨电', bg: 'linear-gradient(135deg, #938BFF 0%, #6A63FF 100%)' },
	{ icon: '💄', title: '美妆', bg: 'linear-gradient(135deg, #FF9FBB 0%, #FF6B91 100%)' },
	{ icon: '🏃', title: '运动', bg: 'linear-gradient(135deg, #5BE8D0 0%, #20C9A0 100%)' },
	{ icon: '📚', title: '图书', bg: 'linear-gradient(135deg, #FFCF5C 0%, #FFB020 100%)' },
	{ icon: '🎁', title: '更多', bg: 'linear-gradient(135deg, #A8B4FF 0%, #7C85FF 100%)' },
]

/* ====== 限时秒杀商品 ====== */
const seckillProducts = [
	{ image: '/static/shop/product-1.png', title: '保温杯316不锈钢', price: '89.00', originPrice: '129.00' },
	{ image: '/static/shop/product-5.png', title: '护眼台灯AA级', price: '129.00', originPrice: '169.00' },
	{ image: '/static/shop/product-8.png', title: '蓝牙音箱便携', price: '199.00', originPrice: '299.00' },
]

/* ====== 优惠券 ====== */
const coupons = [
	{ amount: '50', title: '新人专享券', desc: '满299可用', time: '2026.05.01 到期' },
	{ amount: '30', title: '家电品类券', desc: '满199可用', time: '2026.04.30 到期' },
]

/* ====== 为你推荐瀑布流 ====== */
const recommendProducts = [
	{ image: '/static/shop/product-1.png', title: '简约保温杯大容量316不锈钢便携车载水杯', price: '89.00', originPrice: '129.00', badge: '新品' },
	{ image: '/static/shop/product-2.png', title: '头戴式无线蓝牙耳机主动降噪高音质长续航', price: '299.00', originPrice: '499.00' },
	{ image: '/static/shop/product-3.png', title: '家用半自动咖啡机意式浓缩奶泡一体机', price: '599.00', originPrice: '899.00', badge: '热销' },
	{ image: '/static/shop/product-4.png', title: '简约双肩包大容量通勤旅行学生书包', price: '159.00', originPrice: '199.00' },
	{ image: '/static/shop/product-5.png', title: '护眼台灯学习专用国AA级可调光充电台灯', price: '129.00', originPrice: '169.00' },
	{ image: '/static/shop/product-6.png', title: '全棉四件套纯棉床上用品ins风简约床单被套', price: '249.00', originPrice: '349.00', badge: '新品' },
	{ image: '/static/shop/product-7.png', title: '空气炸锅家用智能多功能无油低脂煎炸锅', price: '369.00', originPrice: '599.00', badge: '热销' },
	{ image: '/static/shop/product-8.png', title: '便携蓝牙音箱高音质低音炮户外迷你音响', price: '199.00', originPrice: '299.00' },
]

/** 页面端按奇偶分列 */
const leftProducts = computed(() => recommendProducts.filter((_, i) => i % 2 === 0))
const rightProducts = computed(() => recommendProducts.filter((_, i) => i % 2 === 1))
</script>

<style lang="scss" scoped>
.shop-page {
	min-height: 100vh;
	background: var(--am-color-bg-page);
	position: relative;
}

.shop-nav {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	z-index: 20;
	background: var(--am-color-bg-page);
	border-bottom: 1rpx solid var(--am-color-border-light);
}

.shop-nav-inner {
	height: 72px;
	padding: 0 28rpx;
	display: flex;
	align-items: center;
	gap: 16rpx;
	box-sizing: border-box;
}

.shop-nav-back {
	width: 56rpx;
	height: 56rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.shop-nav-back-text {
	font-size: 54rpx;
	color: var(--am-color-text-title);
	line-height: 1;
}

.shop-nav-search {
	flex: 1;
	height: 64rpx;
	border-radius: 999rpx;
	background: var(--am-color-bg-muted);
	display: flex;
	align-items: center;
	padding: 0 24rpx;
	gap: 12rpx;
}

.shop-nav-search-icon {
	font-size: 32rpx;
	color: var(--am-color-text-tertiary);
	line-height: 1;
}

.shop-nav-search-placeholder {
	font-size: 26rpx;
	color: var(--am-color-text-tertiary);
	line-height: 1;
}

.shop-scroll {
	height: 100vh;
}

.shop-inner {
	padding: 0 28rpx;
}

.shop-section-card {
	margin-top: 20rpx;
}

.shop-banner {
	border-radius: 22rpx;
	overflow: hidden;
	margin-bottom: 4rpx;
}

/* === 分类入口 === */
.category-item {
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 10rpx;
}

.category-title {
	font-size: 22rpx;
	color: var(--am-color-text-title);
	font-weight: 600;
	line-height: 30rpx;
}

/* === 限时秒杀 === */
.seckill-scroll {
	margin-top: 18rpx;
	white-space: nowrap;
}

.seckill-list {
	display: inline-flex;
	gap: 16rpx;
}

.seckill-item {
	width: 240rpx;
	display: inline-flex;
	flex-direction: column;
	flex-shrink: 0;
}

.seckill-image {
	width: 240rpx;
	height: 240rpx;
	border-radius: 16rpx;
	background: #f5f7fa;
}

.seckill-title {
	margin-top: 12rpx;
	font-size: 22rpx;
	color: var(--am-color-text-title);
	font-weight: 600;
	line-height: 30rpx;
	white-space: normal;
	overflow: hidden;
	text-overflow: ellipsis;
	display: -webkit-box;
	-webkit-line-clamp: 1;
	-webkit-box-orient: vertical;
}

.seckill-price-row {
	display: flex;
	align-items: baseline;
	gap: 8rpx;
	margin-top: 6rpx;
}

.seckill-price {
	font-size: 28rpx;
	color: var(--am-color-danger);
	font-weight: 800;
	line-height: 36rpx;
}

.seckill-origin {
	font-size: 20rpx;
	color: var(--am-color-text-tertiary);
	text-decoration: line-through;
	line-height: 28rpx;
}

/* === 优惠券 === */
.coupon-list {
	display: flex;
	flex-direction: column;
	gap: 16rpx;
	margin-top: 18rpx;
}

/* === 瀑布流商品卡片 === */
.shop-waterfall-card {
	margin-bottom: 20rpx;
}

.wf-card {
	overflow: hidden;
	border-radius: 22rpx;
	background: #ffffff;
	box-shadow: var(--am-shadow-card);
}

.wf-cover {
	position: relative;
	background: linear-gradient(135deg, #eaf2ff 0%, #dbeafe 100%);
}

.wf-image {
	width: 100%;
	display: block;
}

.wf-badge {
	position: absolute;
	top: 12rpx;
	left: 12rpx;
	padding: 4rpx 14rpx;
	border-radius: 8rpx;
	background: var(--am-color-danger);
}

.wf-badge-text {
	font-size: 20rpx;
	color: #ffffff;
	font-weight: 700;
	line-height: 28rpx;
}

.wf-info {
	padding: 18rpx;
}

.wf-title {
	display: block;
	font-size: 26rpx;
	color: var(--am-color-text-title);
	font-weight: 700;
	line-height: 36rpx;
	overflow: hidden;
	text-overflow: ellipsis;
	display: -webkit-box;
	-webkit-line-clamp: 2;
	-webkit-box-orient: vertical;
}

.wf-footer {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-top: 14rpx;
}

.wf-price-wrap {
	display: flex;
	align-items: baseline;
	gap: 8rpx;
}

.wf-price {
	font-size: 30rpx;
	color: var(--am-color-danger);
	font-weight: 800;
	line-height: 38rpx;
}

.wf-origin {
	font-size: 20rpx;
	color: var(--am-color-text-tertiary);
	text-decoration: line-through;
	line-height: 28rpx;
}

.wf-cart {
	width: 52rpx;
	height: 52rpx;
	border-radius: 50%;
	background: var(--am-color-primary);
	display: flex;
	align-items: center;
	justify-content: center;
}

.wf-cart-text {
	font-size: 26rpx;
	color: #ffffff;
	line-height: 1;
}

/* === 轻量模式适配 === */
.am-performance-light .wf-card {
	box-shadow: none;
}

.am-performance-light .shop-banner {
	box-shadow: none;
}
</style>
