<template>
	<ShowcaseShell title="反馈组件" subtitle="Modal / Sheet / Result / Error">
		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="提示 Toast">
				<view class="toast-stack">
					<AmToast visible type="success" text="操作成功" />
					<AmToast visible type="info" text="操作完成" />
					<AmToast visible type="warning" text="请注意填写必填项" />
					<AmToast visible type="error" text="操作失败，请重试" />
				</view>
			</AmSection>
		</AmCard>

		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="弹窗 Modal">
				<AmButton block text="打开弹窗" @tap="showModal = true" />
			</AmSection>
		</AmCard>

		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="底部弹出 Bottom Sheet">
				<AmButton block text="打开底部弹出层" @tap="showBottomSheet = true" />
			</AmSection>
		</AmCard>

		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="操作菜单 Action Sheet">
				<AmButton block variant="secondary" text="打开操作菜单" @tap="showActionSheet = true" />
			</AmSection>
		</AmCard>

		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="加载 Loading">
				<AmLoading text="加载中..." />
			</AmSection>
		</AmCard>

		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="空状态 Empty">
				<AmEmpty title="暂无数据" desc="当前暂无相关内容" />
			</AmSection>
		</AmCard>

		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="骨架屏 Skeleton">
				<AmSkeleton :rows="4" />
			</AmSection>
		</AmCard>

		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="异常状态 ErrorState">
				<AmErrorState type="network" button-text="重新加载" />
			</AmSection>
		</AmCard>

		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="结果页 ResultPage">
				<AmResultPage type="success" primary-text="查看订单" secondary-text="返回首页" />
			</AmSection>
		</AmCard>

		<AmModal :visible="showModal" title="操作成功" desc="这是统一弹窗组件的展示效果。" @close="showModal = false" />
		<AmBottomSheet :visible="showBottomSheet" title="筛选条件" @close="showBottomSheet = false">
			<view class="sheet-options">
				<view class="sheet-option active">
					<text class="sheet-option-text">全部订单</text>
				</view>
				<view class="sheet-option">
					<text class="sheet-option-text">待支付</text>
				</view>
				<view class="sheet-option">
					<text class="sheet-option-text">已完成</text>
				</view>
			</view>
			<template #footer>
				<AmButton block text="确认" @tap="showBottomSheet = false" />
			</template>
		</AmBottomSheet>
		<AmActionSheet
			:visible="showActionSheet"
			title="请选择操作"
			desc="用于移动端底部操作集合"
			:actions="actionSheetItems"
			@close="showActionSheet = false"
			@select="showActionSheet = false"
		/>
	</ShowcaseShell>
</template>

<script setup>
import { ref } from 'vue'
import ShowcaseShell from './components/ShowcaseShell.vue'

const showModal = ref(false)
const showBottomSheet = ref(false)
const showActionSheet = ref(false)

const actionSheetItems = [
	{ label: '设为默认', value: 'default', type: 'primary' },
	{ label: '复制内容', value: 'copy' },
	{ label: '删除记录', value: 'delete', type: 'danger' },
]
</script>

<style lang="scss" scoped>
.demo-card {
	margin-bottom: 22rpx;
}

.toast-stack {
	display: flex;
	flex-direction: column;
	gap: 18rpx;
}

.sheet-options {
	display: flex;
	flex-direction: column;
	gap: 14rpx;
}

.sheet-option {
	height: 76rpx;
	padding: 0 24rpx;
	border-radius: 16rpx;
	background: var(--am-color-bg-soft);
	display: flex;
	align-items: center;
}

.sheet-option.active {
	background: var(--am-color-primary-weak);
}

.sheet-option-text {
	font-size: 26rpx;
	color: var(--am-color-text-body);
	line-height: 36rpx;
}

.sheet-option.active .sheet-option-text {
	color: var(--am-color-primary);
	font-weight: 600;
}
</style>
