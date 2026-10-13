<template>
	<ShowcaseShell title="首页组合组件" subtitle="Home Header / Quick Entry / Template Card">
		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="AmHomeHeader">
				<view class="header-stack">
					<AmHomeHeader
						title="发现今天的新内容"
						subtitle="精选动态、热门分类和社区消息"
						:actions="headerActions"
						:safe-area="false"
					/>
					<AmHomeHeader
						variant="gradient"
						title="发现今天的新内容"
						subtitle="精选动态、热门分类和社区消息"
						:actions="headerActions"
						:safe-area="false"
					/>
					<AmHomeHeader
						variant="image"
						title="探索世界的美好内容"
						subtitle="精选动态、热门分类和社区消息"
						background-image="/static/showcase/home-header-bg.png"
						:actions="headerActions"
						:safe-area="false"
					/>
					<AmHomeHeader
						title="发现今天的新内容"
						subtitle="精选动态、热门分类和社区消息"
						:actions="customActions"
						avatar="/static/default-avatar.png"
						:safe-area="false"
					/>
				</view>
			</AmSection>
		</AmCard>

		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="AmQuickEntry">
				<view class="quick-grid">
					<AmQuickEntry
						variant="primary"
						icon="✎"
						title="发布动态"
						desc="分享你的生活，记录美好瞬间"
						badge="推荐"
					/>
					<AmQuickEntry icon="▦" title="热门分类" desc="发现更多感兴趣的优质内容" />
					<AmQuickEntry variant="success" icon="▣" title="消息中心" desc="查看互动消息和通知" badge="8" />
					<AmQuickEntry variant="warm" icon="◈" title="组件总览" desc="探索全部组件，快速搭建页面" />
				</view>
				<view class="compact-row">
					<AmQuickEntry v-for="item in compactEntries" :key="item.icon" compact :icon="item.icon" :badge="item.badge" />
				</view>
			</AmSection>
		</AmCard>

		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="AmTemplateCard">
				<view class="template-grid">
					<AmTemplateCard
						v-for="item in templates"
						:key="item.title"
						:cover="item.cover"
						:title="item.title"
						:desc="item.desc"
						:tags="item.tags"
						:badge="item.badge"
						:status="item.status"
						:status-type="item.statusType"
					/>
				</view>
				<view class="template-list">
					<AmTemplateCard
						v-for="item in compactTemplates"
						:key="item.title"
						compact
						:cover="item.cover"
						:title="item.title"
						:desc="item.desc"
						:status="item.status"
						:status-type="item.statusType"
					/>
				</view>
			</AmSection>
		</AmCard>
	</ShowcaseShell>
</template>

<script setup>
import ShowcaseShell from './components/ShowcaseShell.vue'

const headerActions = [
	{ key: 'search', icon: '⌕' },
	{ key: 'notice', icon: '◌', badge: '12' },
]

const customActions = [
	{ key: 'publish', icon: '+', text: '发布' },
]

const compactEntries = [
	{ icon: '✎' },
	{ icon: '▦' },
	{ icon: '▣', badge: '8' },
	{ icon: '◈' },
]

const templates = [
	{
		cover: '/static/home-bg.png',
		title: '社区首页模板',
		desc: '完整的社区首页框架，包含轮播、分类、动态流、活动等模块。',
		tags: ['首页模板', '社区', '多模块'],
		badge: '推荐',
		status: '已发布',
		statusType: 'success',
	},
	{
		cover: '/static/login-bg.png',
		title: '个人中心模板',
		desc: '个人主页、数据统计、内容管理、设置等功能完整集成。',
		tags: ['个人中心', '用户', '数据展示'],
		badge: '热门',
		status: '已发布',
		statusType: 'success',
	},
	{
		cover: '/static/profile-bg.png',
		title: '电商商城模板',
		desc: '商品展示、购物车、订单、支付等电商核心流程组件化。',
		tags: ['电商', '商城', '交易'],
		status: '开发中',
		statusType: 'warning',
	},
	{
		cover: '/static/home-bg.png',
		title: '活动页面模板',
		desc: '活动专题页、报名、分享、规则说明等运营页面能力。',
		tags: ['活动', '运营', '专题页'],
		badge: '官方',
		status: '官方推荐',
		statusType: 'primary',
	},
]

const compactTemplates = [
	{ cover: '/static/home-bg.png', title: '社区首页模板', desc: '首页流和内容入口', status: '已发布', statusType: 'success' },
	{ cover: '/static/login-bg.png', title: '个人中心模板', desc: '用户信息和设置入口', status: '已发布', statusType: 'success' },
	{ cover: '/static/profile-bg.png', title: '电商商城模板', desc: '商品订单和交易流程', status: '开发中', statusType: 'warning' },
]
</script>

<style lang="scss" scoped>
.demo-card {
	margin-bottom: 22rpx;
}

.header-stack {
	display: flex;
	flex-direction: column;
	gap: 20rpx;
}

.quick-grid,
.template-grid {
	display: grid;
	grid-template-columns: repeat(2, 1fr);
	gap: 18rpx;
}

.compact-row {
	margin-top: 20rpx;
	padding: 16rpx;
	border-radius: 24rpx;
	background: #ffffff;
	box-shadow: var(--am-shadow-card);
	display: grid;
	grid-template-columns: repeat(4, 1fr);
	gap: 14rpx;
}

.template-list {
	margin-top: 20rpx;
	display: flex;
	flex-direction: column;
	gap: 14rpx;
}

.am-performance-light .compact-row {
	box-shadow: none;
}
</style>
