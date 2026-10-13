<template>
	<view class="med-page" :class="pagePerformanceClass" @touchstart="handleTouchStart" @touchend="handleTouchEnd">
		<!-- 顶部导航栏：返回 + 搜索框 -->
		<view class="med-nav" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="med-nav-inner">
				<view class="med-nav-back" @tap="goBack">
					<text class="med-nav-back-text">‹</text>
				</view>
				<view class="med-nav-search" @tap="showComingSoon('搜索')">
					<text class="med-nav-search-icon">⌕</text>
					<text class="med-nav-search-placeholder">搜索医院、医生、疾病、科室</text>
				</view>
			</view>
		</view>

		<scroll-view class="med-scroll" scroll-y :enhanced="scrollEnhanced" :bounces="scrollBounces" :show-scrollbar="scrollShowScrollbar" :lower-threshold="scrollLowerThreshold">
			<view class="med-inner" :style="[contentStyle, { paddingTop: (statusBarHeight + 72) + 'px' }]">

				<!-- Banner 轮播 -->
				<view class="med-banner">
					<AmCarousel :items="bannerItems" autoplay :interval="4000" />
				</view>

				<!-- 金刚区 -->
				<AmCard class="med-section-card" padding="26rpx">
					<AmGrid :columns="4" row-gap="24rpx">
						<view class="category-item" v-for="item in categoryEntries" :key="item.title" @tap="showComingSoon(item.title)">
							<AmIconBox :background="item.bg" :shadow="false" size="88rpx">
								<image class="category-icon-img" :src="item.iconImg" mode="aspectFit" />
							</AmIconBox>
							<text class="category-title">{{ item.title }}</text>
						</view>
					</AmGrid>
				</AmCard>

				<!-- 就诊提醒 -->
				<AmTipCard
					class="med-section-card"
					icon="🔔"
					title="您今日 14:30 有骨科门诊预约（张医生），请提前 15 分钟到达"
					:arrow="true"
					:clickable="true"
					@tap="showComingSoon('就诊详情')"
				/>

				<!-- 快速问诊 -->
				<view class="med-consult-row">
					<AmQuickEntry
						class="med-consult-item"
						title="图文问诊"
						desc="3 分钟响应 · 专业医生在线解答"
						icon="✎"
						icon-background="linear-gradient(135deg, #72AEFF 0%, #4B8FFF 100%)"
						icon-color="#ffffff"
						background="linear-gradient(135deg, #EEF4FF 0%, #DBEAFE 100%)"
						@tap="showComingSoon('图文问诊')"
					/>
					<AmQuickEntry
						class="med-consult-item"
						title="视频问诊"
						desc="面对面沟通 · 更直观 更安心"
						icon="▶"
						icon-background="linear-gradient(135deg, #72E7C0 0%, #38D6A0 100%)"
						icon-color="#ffffff"
						background="linear-gradient(135deg, #ECFDF5 0%, #D1FAE5 100%)"
						@tap="showComingSoon('视频问诊')"
					/>
				</view>

				<!-- 推荐医生 -->
				<AmCard class="med-section-card" padding="0">
					<view style="padding: 26rpx 26rpx 0;">
						<AmSection title="推荐医生" more-text="更多" @more="showComingSoon('更多医生')" />
					</view>
					<view class="med-doctor-list">
						<AmDoctorCard
							v-for="doc in doctors"
							:key="doc.name"
							:avatar="doc.avatar"
							:name="doc.name"
							:department="doc.department"
							:title="doc.title"
							:title-badge="doc.titleBadge"
							:hospital="doc.hospital"
							:specialty="doc.specialty"
							:score="doc.score"
							:consult-count="doc.consultCount"
							@consult="showComingSoon('问诊 - ' + doc.name)"
						/>
					</view>
				</AmCard>

				<!-- 今日用药 -->
				<AmCard class="med-section-card" padding="26rpx">
					<AmSection title="今日用药" />
					<view class="med-medicine-list">
						<AmMedicineCard
							v-for="med in medicines"
							:key="med.name"
							:icon-image="med.iconImage"
							:icon-bg="med.iconBg"
							:name="med.name"
							:dosage="med.dosage"
							:next-time="med.nextTime"
							:taken="med.taken"
							@take="showComingSoon('服用 - ' + med.name)"
						/>
					</view>
				</AmCard>

				<!-- 健康科普 -->
				<AmCard class="med-section-card med-last-card" padding="26rpx">
					<AmSection title="健康科普" more-text="更多" @more="showComingSoon('更多科普')" />
					<view class="med-article-list">
						<AmMediaCard
							v-for="art in articles"
							:key="art.title"
							:cover="art.cover"
							:title="art.title"
							:desc="art.desc"
							:meta="art.meta"
							horizontal
							@tap="showComingSoon(art.title)"
						/>
					</view>
				</AmCard>
			</view>
		</scroll-view>

		<AmModal :visible="showModal" :title="modalTitle" @close="showModal = false" />
	</view>
</template>

<script setup>
import { ref } from 'vue'
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

/* ====== Banner ====== */
const bannerItems = [
	{ image: '/static/medical/article-1.png', title: '春季体检套餐', desc: '全面检查 呵护健康' },
	{ image: '/static/medical/article-2.png', title: '名医义诊活动', desc: '专家在线 免费咨询' },
	{ image: '/static/medical/article-3.png', title: '健康管理讲座', desc: '科学管理 健康生活' },
]

/* ====== 金刚区 ====== */
const categoryEntries = [
	{ iconImg: '/static/medical/icon-consult.png', title: '在线问诊', bg: 'linear-gradient(135deg, #72AEFF 0%, #4B8FFF 100%)' },
	{ iconImg: '/static/medical/icon-appointment.png', title: '预约挂号', bg: 'linear-gradient(135deg, #72E7C0 0%, #38D6A0 100%)' },
	{ iconImg: '/static/medical/icon-report.png', title: '报告查询', bg: 'linear-gradient(135deg, #938BFF 0%, #6A63FF 100%)' },
	{ iconImg: '/static/medical/icon-medicine.png', title: '用药提醒', bg: 'linear-gradient(135deg, #FFB86C 0%, #FF7A45 100%)' },
	{ iconImg: '/static/medical/icon-family.png', title: '家庭档案', bg: 'linear-gradient(135deg, #FF9FBB 0%, #FF6B91 100%)' },
	{ iconImg: '/static/medical/icon-checkup.png', title: '健康体检', bg: 'linear-gradient(135deg, #5BE8D0 0%, #20C9A0 100%)' },
	{ iconImg: '/static/medical/icon-vaccine.png', title: '疫苗预约', bg: 'linear-gradient(135deg, #FFCF5C 0%, #FFB020 100%)' },
	{ iconImg: '/static/medical/icon-more.png', title: '更多', bg: 'linear-gradient(135deg, #A8B4FF 0%, #7C85FF 100%)' },
]

/* ====== 推荐医生 ====== */
const doctors = [
	{
		avatar: '/static/medical/doctor-1.png', name: '张建国', department: '骨科', title: '主任医师',
		titleBadge: '主任', hospital: '北京协和医院',
		specialty: '颈椎病、腰椎间盘突出、关节置换、运动损伤康复',
		score: '4.9', consultCount: '1280',
	},
	{
		avatar: '/static/medical/doctor-2.png', name: '李文静', department: '内分泌科', title: '副主任医师',
		titleBadge: '副主任', hospital: '上海瑞金医院',
		specialty: '糖尿病、甲状腺疾病、骨质疏松、内分泌失调',
		score: '4.8', consultCount: '986',
	},
	{
		avatar: '/static/medical/doctor-3.png', name: '王浩然', department: '心内科', title: '主治医师',
		titleBadge: '主治医师', hospital: '中山大学附属第一医院',
		specialty: '高血压、冠心病、心律失常、心脏瓣膜病',
		score: '4.9', consultCount: '756',
	},
]

/* ====== 今日用药 ====== */
const medicines = [
	{
		iconImage: '/static/medical/icon-medicine.png',
		iconBg: 'linear-gradient(135deg, #72AEFF 0%, #4B8FFF 100%)',
		name: '阿莫西林胶囊', dosage: '每次2粒 · 每日3次 · 饭后服用', nextTime: '14:00', taken: false,
	},
	{
		iconImage: '/static/medical/icon-medicine.png',
		iconBg: 'linear-gradient(135deg, #72E7C0 0%, #38D6A0 100%)',
		name: '维生素C片', dosage: '每次1片 · 每日2次 · 饭后服用', nextTime: '20:00', taken: true,
	},
]

/* ====== 健康科普 ====== */
const articles = [
	{
		cover: '/static/medical/article-1.png', title: '春季过敏防护指南',
		desc: '春季花粉高发，教你科学预防过敏，远离鼻炎、皮肤瘙痒等困扰。',
		meta: '1.2万阅读 · 856收藏',
	},
	{
		cover: '/static/medical/article-2.png', title: '高血压日常管理要点',
		desc: '从饮食、运动、用药三方面，帮你稳定控血压，降低并发风险。',
		meta: '8,765阅读 · 642收藏',
	},
	{
		cover: '/static/medical/article-3.png', title: '如何正确测量血糖',
		desc: '掌握正确的血糖监测方法，数据更准确，帮助更好地管理血糖。',
		meta: '6,543阅读 · 512收藏',
	},
]
</script>

<style lang="scss" scoped>
.med-page {
	min-height: 100vh;
	background: var(--am-color-bg-page);
	position: relative;
}

.med-nav {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	z-index: 20;
	background: var(--am-color-bg-page);
	border-bottom: 1rpx solid var(--am-color-border-light);
}

.med-nav-inner {
	height: 72px;
	padding: 0 28rpx;
	display: flex;
	align-items: center;
	gap: 16rpx;
	box-sizing: border-box;
}

.med-nav-back {
	width: 56rpx;
	height: 56rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.med-nav-back-text {
	font-size: 54rpx;
	color: var(--am-color-text-title);
	line-height: 1;
}

.med-nav-search {
	flex: 1;
	height: 64rpx;
	border-radius: 999rpx;
	background: var(--am-color-bg-muted);
	display: flex;
	align-items: center;
	padding: 0 24rpx;
	gap: 12rpx;
}

.med-nav-search-icon {
	font-size: 32rpx;
	color: var(--am-color-text-tertiary);
	line-height: 1;
}

.med-nav-search-placeholder {
	font-size: 26rpx;
	color: var(--am-color-text-tertiary);
	line-height: 1;
}

.med-scroll {
	height: 100vh;
}

.med-inner {
	padding: 0 28rpx;
}

.med-section-card {
	margin-top: 20rpx;
}

.med-banner {
	border-radius: 22rpx;
	overflow: hidden;
	margin-bottom: 4rpx;
}

/* === 金刚区图标 === */
.category-item {
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 10rpx;
}

.category-icon-img {
	width: 48rpx;
	height: 48rpx;
}

.category-title {
	font-size: 22rpx;
	color: var(--am-color-text-title);
	font-weight: 600;
	line-height: 30rpx;
}

/* === 快速问诊并排 === */
.med-consult-row {
	display: flex;
	gap: 16rpx;
	margin-top: 20rpx;
}

.med-consult-item {
	flex: 1;
	min-width: 0;
}

/* === 医生列表 === */
.med-doctor-list {
	display: flex;
	flex-direction: column;
	gap: 16rpx;
	padding: 0 26rpx 26rpx;
}

/* === 用药列表 === */
.med-medicine-list {
	display: flex;
	flex-direction: column;
	gap: 16rpx;
	margin-top: 4rpx;
}

/* === 科普文章 === */
.med-article-list {
	display: flex;
	flex-direction: column;
	gap: 20rpx;
	margin-top: 4rpx;
}

.med-last-card {
	margin-bottom: 20rpx;
}

/* === 轻量模式 === */
.am-performance-light .med-banner {
	box-shadow: none;
}
</style>
