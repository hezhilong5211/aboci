<template>
	<ShowcaseShell title="轮播组件" subtitle="Banner / Card / Thumb / Promo">
		<AmCard v-if="!ready" class="demo-card" padding="28rpx">
			<text class="loading-text">轮播组件加载中...</text>
		</AmCard>

		<AmCard v-if="ready" class="demo-card" padding="28rpx">
			<AmSection title="基础轮播">
				<AmCarousel :items="coverItems" variant="basic" />
			</AmSection>
		</AmCard>

		<AmCard v-if="ready" class="demo-card" padding="28rpx">
			<AmSection title="卡片轮播">
				<AmCarousel :items="cardItems" variant="card" />
			</AmSection>
		</AmCard>

		<AmCard v-if="ready" class="demo-card" padding="28rpx">
			<AmSection title="缩略图导航">
				<AmCarousel :items="coverItems" variant="thumb" />
			</AmSection>
		</AmCard>

		<AmCard v-if="ready" class="demo-card" padding="28rpx">
			<AmSection title="图文轮播">
				<AmCarousel :items="contentItems" variant="content" />
			</AmSection>
		</AmCard>

		<AmCard v-if="ready" class="demo-card" padding="28rpx">
			<AmSection title="活动 Banner">
				<AmCarousel :items="promoItems" variant="promo" />
			</AmSection>
		</AmCard>

		<AmCard v-if="ready" class="demo-card" padding="28rpx">
			<AmSection title="自动播放 + 控制条">
				<AmCarousel :items="controlItems" variant="control" autoplay :interval="3600" />
			</AmSection>
		</AmCard>
	</ShowcaseShell>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import ShowcaseShell from './components/ShowcaseShell.vue'

const ready = ref(false)

onMounted(() => {
	setTimeout(() => {
		ready.value = true
	}, 80)
})

const coverItems = [
	{
		id: 'lake',
		image: '/static/home-bg.png',
		title: '探索世界的美好',
		desc: '用清晰层级承载图片内容和文字信息',
	},
	{
		id: 'city',
		image: '/static/login-bg.png',
		title: '城市里的日常',
		desc: '适合首页 Banner、专题和运营推荐',
	},
	{
		id: 'profile',
		image: '/static/profile-bg.png',
		title: '记录生活灵感',
		desc: '支持圆点、计数和缩略图导航',
	},
]

const cardItems = [
	{
		id: 'card-1',
		image: '/static/login-bg.png',
		title: '圣托里尼的日落',
		desc: '卡片式展示，适合内容精选',
	},
	{
		id: 'card-2',
		image: '/static/home-bg.png',
		title: '山谷湖畔',
		desc: '图片、标题、说明和页码组合',
	},
]

const contentItems = [
	{
		id: 'space',
		image: '/static/profile-bg.png',
		title: '打造舒适的家居空间',
		desc: '左图右文结构，适合商品、文章和服务介绍。',
		buttonText: '了解更多',
	},
	{
		id: 'travel',
		image: '/static/home-bg.png',
		title: '自然之美',
		desc: '保持内容可读性，也能展示图片氛围。',
		buttonText: '立即查看',
	},
]

const promoItems = [
	{
		id: 'spring',
		kicker: '限时优惠',
		title: '春季大促销',
		desc: '精选内容与服务限时开放',
		buttonText: '立即参与',
		image: '/static/default-avatar.png',
		background: 'linear-gradient(135deg, #FF8CA8 0%, #FFB06C 100%)',
	},
	{
		id: 'member',
		kicker: '会员专享',
		title: '内容权益升级',
		desc: '更清晰的活动入口和转化按钮',
		buttonText: '查看权益',
		image: '/static/default-avatar.png',
		background: 'linear-gradient(135deg, #4F8CFF 0%, #6D5BFF 100%)',
	},
]

const controlItems = [
	{
		id: 'wave',
		image: '/static/home-bg.png',
		title: '自动播放',
		desc: '支持左右切换和暂停控制',
	},
	{
		id: 'mountain',
		image: '/static/login-bg.png',
		title: '控制条轮播',
		desc: '适合视频封面、活动焦点图和内容推荐',
	},
	{
		id: 'coast',
		image: '/static/profile-bg.png',
		title: '沉浸式展示',
		desc: '保留底部指示器和右上角计数',
	},
]
</script>

<style lang="scss" scoped>
.demo-card {
	margin-bottom: 22rpx;
}

.loading-text {
	display: block;
	font-size: 26rpx;
	color: var(--am-color-text-secondary);
	line-height: 38rpx;
	text-align: center;
}
</style>
