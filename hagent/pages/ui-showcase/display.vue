<template>
	<ShowcaseShell title="展示组件" subtitle="Content / Commerce / Chat / Timeline">
		<view class="stats-grid">
			<AmStatsCard title="今日访问" value="1,268" trend="+12.5%" desc="较昨日提升" />
			<AmStatsCard title="支付订单" value="856" trend="-3.1%" trend-type="danger" desc="较昨日下降" />
		</view>

		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="头像与徽标">
				<view class="avatar-row">
					<AmAvatar src="/static/default-avatar.png" size="72rpx" />
					<AmAvatar text="极" size="72rpx" />
					<AmBadge :value="12">
						<AmIconBox icon="⌁" background="transparent" size="60rpx" color="#263959" :shadow="false" />
					</AmBadge>
				</view>
			</AmSection>
		</AmCard>

		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="切换 Segmented / Tabs">
				<AmSegmented v-model="segmentValue" :options="segmentOptions" />
				<view class="tabs-demo">
					<AmTabs v-model="tabValue" :options="tabOptions" />
				</view>
			</AmSection>
		</AmCard>

		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="列表 List">
				<view class="list-panel">
					<AmListItem title="个人中心" desc="查看和编辑个人信息">
						<template #icon>
							<AmAvatar src="/static/default-avatar.png" size="72rpx" />
						</template>
					</AmListItem>
					<AmListItem title="消息通知" desc="查看最新消息">
						<template #icon>
							<AmBadge :value="12">
								<AmIconBox icon="⌁" background="transparent" size="60rpx" color="#263959" :shadow="false" />
							</AmBadge>
						</template>
					</AmListItem>
					<AmListItem title="系统设置" desc="偏好与安全设置">
						<template #icon>
							<AmIconBox icon="⚙" background="transparent" size="60rpx" color="#263959" :shadow="false" />
						</template>
					</AmListItem>
				</view>
			</AmSection>
		</AmCard>

		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="下拉 Dropdown">
				<view class="dropdown-demo">
					<AmDropdown v-model="dropdownValue" :options="dropdownOptions" placeholder="请选择排序方式" />
				</view>
			</AmSection>
		</AmCard>

		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="时间轴 Timeline">
				<AmTimeline :items="timelineItems" />
			</AmSection>
		</AmCard>

		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="聊天 ChatBubble">
				<view class="chat-demo">
					<AmChatBubble
						avatar-text="顾"
						name="顾问小林"
						text="您好，我可以帮您查看账户和订单相关问题。"
						time="10:20"
						@avatar-tap="showProfileSheet = true"
					/>
					<AmChatBubble type="self" avatar-text="我" name="我" text="我想了解一下会员服务。" time="10:21" status="已送达" />
				</view>
				<AmButton text="查看对方资料" variant="secondary" @tap="showProfileSheet = true" />
			</AmSection>
		</AmCard>

		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="内容 Content">
				<view class="content-stack">
					<AmAuthorBar name="设计观察" desc="2小时前发布" tag="作者" right-text="关注" />
					<AmMediaCard
						cover="/static/home-bg.png"
						title="移动端首页视觉升级"
						desc="用统一组件承载图文、视频和作者信息。"
						tag="图文"
						meta="1.2k 阅读"
					/>
					<AmCommentItem author="小陈" content="组件拆分之后，真实页面拼装会更稳定。" time="刚刚" like-text="赞 12" />
					<AmActionBar :actions="contentActions" />
				</view>
			</AmSection>
		</AmCard>

		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="电商与订单 Commerce">
				<view class="content-stack">
					<AmProductCard title="会员专属服务包" desc="顾问响应、资料权益、订单提醒" price="199" origin-price="299" :tags="['热销', '会员']" horizontal />
					<AmCouponCard amount="30" title="新人优惠券" desc="满 199 可用" time="有效期至 2026-05-31" />
					<AmAddressCard name="张小明" phone="138****8888" address="上海市浦东新区 Aurora 科技园 8 号楼" is-default />
					<AmOrderStatus title="订单配送中" desc="预计今日 18:00 前送达" status-text="配送中" :steps="orderSteps" :current="2" />
					<AmPriceDetail :items="priceItems" total="¥169.00" />
				</view>
			</AmSection>
		</AmCard>

		<AmProfileSheet
			:visible="showProfileSheet"
			:profile="profileInfo"
			button-text="开始咨询"
			@close="showProfileSheet = false"
			@action="showProfileSheet = false"
		/>
	</ShowcaseShell>
</template>

<script setup>
import { ref } from 'vue'
import ShowcaseShell from './components/ShowcaseShell.vue'

const segmentValue = ref('all')
const tabValue = ref('recommend')
const dropdownValue = ref('latest')
const showProfileSheet = ref(false)

const segmentOptions = [
	{ label: '全部', value: 'all' },
	{ label: '待付款', value: 'pay' },
	{ label: '待发货', value: 'ship' },
	{ label: '待收货', value: 'receive' },
]

const tabOptions = [
	{ label: '推荐', value: 'recommend' },
	{ label: '关注', value: 'follow' },
	{ label: '发现', value: 'discover' },
	{ label: '我的', value: 'mine' },
]

const dropdownOptions = [
	{ label: '最新发布', value: 'latest' },
	{ label: '销量优先', value: 'sales' },
	{ label: '评分最高', value: 'rating' },
]

const timelineItems = [
	{ title: '提交订单', desc: '订单已创建，等待支付', time: '09:20', active: true },
	{ title: '支付成功', desc: '系统已确认付款', time: '09:26', active: true },
	{ title: '等待发货', desc: '商家正在处理', time: '今天' },
]

const contentActions = [
	{ key: 'like', icon: '♡', label: '点赞', count: '128', active: true },
	{ key: 'star', icon: '☆', label: '收藏', count: '36' },
	{ key: 'share', icon: '↗', label: '分享' },
]

const orderSteps = ['下单', '支付', '配送', '完成']

const priceItems = [
	{ label: '商品金额', value: '¥199.00' },
	{ label: '优惠券', value: '-¥30.00', discount: true },
	{ label: '配送费', value: '¥0.00' },
]

const profileInfo = {
	name: '顾问小林',
	avatar: '',
	desc: '高级客户顾问，擅长账户安全与订单处理',
	tags: ['在线', '响应快', '认证顾问'],
	stats: [
		{ label: '满意度', value: '98%' },
		{ label: '服务数', value: '1.2k' },
		{ label: '响应', value: '1分钟' },
	],
	info: [
		{ label: '服务时间', value: '09:00-21:00' },
		{ label: '所属团队', value: '客户成功组' },
		{ label: '擅长问题', value: '会员/订单/售后' },
	],
}
</script>

<style lang="scss" scoped>
.demo-card {
	margin-bottom: 22rpx;
}

.stats-grid {
	display: grid;
	grid-template-columns: repeat(2, 1fr);
	gap: 20rpx;
	margin-bottom: 22rpx;
}

.avatar-row {
	display: flex;
	align-items: center;
	gap: 22rpx;
}

.tabs-demo {
	margin-top: 24rpx;
}

.list-panel {
	border: 1rpx solid var(--am-color-border-light);
	border-radius: 20rpx;
	padding: 0 20rpx;
}

.dropdown-demo {
	min-height: 240rpx;
}

.chat-demo {
	padding: 18rpx;
	border-radius: 20rpx;
	background: var(--am-color-bg-soft);
	margin-bottom: 20rpx;
}

.content-stack {
	display: flex;
	flex-direction: column;
	gap: 20rpx;
}
</style>
