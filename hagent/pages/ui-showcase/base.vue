<template>
	<ShowcaseShell title="基础组件" subtitle="Button / Card / Icon / Tag / FAB">
		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="按钮 Button">
				<view class="button-row">
					<AmButton text="主按钮" />
					<AmButton text="次按钮" variant="secondary" />
					<AmButton text="删除" variant="danger" />
				</view>
				<view class="button-row">
					<AmButton text="小按钮" size="small" />
					<AmButton text="文字按钮" variant="text" />
					<AmButton text="禁用" disabled />
				</view>
			</AmSection>
		</AmCard>

		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="卡片 Card">
				<AmCard class="inner-card" padding="24rpx">
					<text class="card-title">默认卡片</text>
					<text class="card-desc">用于承载页面分组内容。</text>
				</AmCard>
				<AmCard class="inner-card" variant="soft" padding="24rpx">
					<text class="card-title">柔和卡片</text>
					<text class="card-desc">用于会员卡、提示区等弱强调内容。</text>
				</AmCard>
			</AmSection>
		</AmCard>

		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="图标容器 IconBox">
				<AmGrid :columns="4" row-gap="26rpx">
					<view class="icon-demo" v-for="item in iconDemos" :key="item.text">
						<AmIconBox :icon="item.icon" :background="item.bg" />
						<text class="icon-demo-text">{{ item.text }}</text>
					</view>
				</AmGrid>
			</AmSection>
		</AmCard>

		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="标签 Tag">
				<view class="tag-row">
					<AmTag text="全部" />
					<AmTag text="成功" type="success" />
					<AmTag text="警告" type="warning" />
					<AmTag text="失败" type="danger" />
					<AmTag text="描边" plain />
				</view>
			</AmSection>
		</AmCard>

		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="进度 Progress">
				<view class="progress-demo">
					<AmProgress :percent="68" show-text />
				</view>
				<view class="progress-demo">
					<AmProgress :percent="36" show-text />
				</view>
			</AmSection>
		</AmCard>

		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="悬浮按钮 FAB">
				<view class="fab-row">
					<AmFab :fixed="false" icon="＋" />
					<AmFab :fixed="false" icon="✎" text="发布" />
					<AmFab :fixed="false" icon="✓" type="success" />
				</view>
			</AmSection>
		</AmCard>
	</ShowcaseShell>
</template>

<script setup>
import ShowcaseShell from './components/ShowcaseShell.vue'

const iconDemos = [
	{ icon: '⌂', text: '首页', bg: 'linear-gradient(135deg, #5B9DFF 0%, #3E7DFF 100%)' },
	{ icon: '▤', text: '知识', bg: 'linear-gradient(135deg, #72E7C0 0%, #38D6A0 100%)' },
	{ icon: '◉', text: '咨询', bg: 'linear-gradient(135deg, #938BFF 0%, #6A63FF 100%)' },
	{ icon: '★', text: '收藏', bg: 'linear-gradient(135deg, #FFC97B 0%, #FFA245 100%)' },
]
</script>

<style lang="scss" scoped>
.demo-card,
.inner-card {
	margin-bottom: 22rpx;
}

.inner-card:last-child {
	margin-bottom: 0;
}

.button-row,
.tag-row {
	display: flex;
	align-items: center;
	gap: 18rpx;
	margin-bottom: 18rpx;
	flex-wrap: wrap;
}

.button-row:last-child {
	margin-bottom: 0;
}

.card-title {
	display: block;
	font-size: 28rpx;
	color: var(--am-color-text-title);
	font-weight: 700;
	line-height: 38rpx;
}

.card-desc {
	display: block;
	margin-top: 8rpx;
	font-size: 22rpx;
	color: var(--am-color-text-secondary);
	line-height: 32rpx;
}

.icon-demo {
	display: flex;
	flex-direction: column;
	align-items: center;
}

.icon-demo-text {
	margin-top: 12rpx;
	font-size: 22rpx;
	color: var(--am-color-text-body);
	line-height: 30rpx;
}

.progress-demo {
	margin-bottom: 26rpx;
}

.progress-demo:last-child {
	margin-bottom: 0;
}

.fab-row {
	display: flex;
	align-items: center;
	gap: 22rpx;
	flex-wrap: wrap;
}
</style>
