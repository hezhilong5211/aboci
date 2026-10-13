<template>
	<view class="conversation-nav" :style="rootStyle">
		<view class="conversation-nav__bar" :style="barStyle">
			<view v-if="mode === 'chat'" class="conversation-nav__actions">
				<view class="conversation-nav__history" @tap="$emit('open-history')">
					<view class="conversation-nav__history-surface">
						<image class="conversation-nav__history-icon" src="/static/history.png" mode="aspectFit" />
					</view>
				</view>
				<view v-if="showBalance" class="conversation-nav__balance" @tap="$emit('recharge')">
					<image class="conversation-nav__coin" src="/static/豆子.png" mode="aspectFit" />
					<text class="conversation-nav__balance-text">{{ balanceText }}</text>
				</view>
			</view>

			<template v-else-if="mode === 'history'">
				<view class="conversation-nav__back" @tap="$emit('back')">
					<text class="conversation-nav__back-icon">‹</text>
				</view>
				<text class="conversation-nav__title">历史记录</text>
			</template>

			<scroll-view
				v-else-if="mode === 'tabs'"
				class="conversation-nav__tabs"
				scroll-x
				:show-scrollbar="false"
				:scroll-into-view="activeTabDomId"
				:scroll-with-animation="true"
			>
				<view class="conversation-nav__tab-list">
					<view
						v-for="tab in tabs"
						:id="navigationTabId(tab.key)"
						:key="tab.key"
						class="conversation-nav__tab"
						:class="{
							'conversation-nav__tab--active': isActiveTab(tab),
							'conversation-nav__tab--disabled': tab.disabled,
						}"
						@tap="$emit('select-tab', tab)"
					>
						<text class="conversation-nav__tab-text">{{ tab.label }}</text>
						<view v-if="isActiveTab(tab)" class="conversation-nav__tab-underline" />
					</view>
				</view>
			</scroll-view>
		</view>
	</view>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
	mode: { type: String, default: 'blank' },
	statusBarHeight: { type: Number, default: 20 },
	navigationBarHeight: { type: Number, default: 44 },
	rightSafeWidth: { type: Number, default: 96 },
	showBalance: { type: Boolean, default: false },
	balanceText: { type: String, default: '0' },
	tabs: { type: Array, default: () => [] },
	activeTabKey: { type: [String, Number], default: '' },
})

defineEmits(['open-history', 'recharge', 'back', 'select-tab'])

const rootStyle = computed(() => ({
	height: `${props.statusBarHeight + props.navigationBarHeight}px`,
	paddingTop: `${props.statusBarHeight}px`,
}))

const barStyle = computed(() => ({
	height: `${props.navigationBarHeight}px`,
	paddingRight: `${props.rightSafeWidth}px`,
}))

function navigationTabId(key) {
	return `conversation-nav-tab-${String(key).replace(/[^a-zA-Z0-9_-]/g, '-')}`
}

function isActiveTab(tab) {
	return String(tab.key) === String(props.activeTabKey)
}

const activeTabDomId = computed(() =>
	props.activeTabKey === '' || props.activeTabKey == null
		? ''
		: navigationTabId(props.activeTabKey)
)
</script>

<style lang="scss" scoped>
.conversation-nav {
	position: relative;
	z-index: 60;
	width: 100%;
	flex-shrink: 0;
	box-sizing: border-box;
	background: #f6f7fb;
}

.conversation-nav__bar {
	position: relative;
	width: 100%;
	display: flex;
	align-items: center;
	box-sizing: border-box;
}

.conversation-nav__actions {
	display: flex;
	align-items: center;
	min-width: 0;
	max-width: 100%;
	padding-left: 12px;
	gap: 4px;
	box-sizing: border-box;
	overflow: hidden;
}

.conversation-nav__history,
.conversation-nav__back {
	width: 44px;
	height: 44px;
	flex-shrink: 0;
	display: flex;
	align-items: center;
	justify-content: center;
}

.conversation-nav__history-surface {
	width: 32px;
	height: 32px;
	border-radius: 50%;
	border: 1rpx solid #e3e6ec;
	background: #fff;
	display: flex;
	align-items: center;
	justify-content: center;
	transition: transform 0.12s ease, opacity 0.12s ease;
}

.conversation-nav__history:active .conversation-nav__history-surface {
	transform: scale(0.96);
	opacity: 0.9;
}

.conversation-nav__history-icon {
	width: 20px;
	height: 20px;
}

.conversation-nav__balance {
	height: 30px;
	min-width: 0;
	max-width: 132px;
	padding: 0 11px;
	border-radius: 15px;
	border: 1rpx solid rgba(96, 140, 224, 0.2);
	background: #deeafb;
	display: flex;
	align-items: center;
	gap: 6px;
	box-sizing: border-box;
	transition: opacity 0.12s ease;
}

.conversation-nav__balance:active {
	opacity: 0.86;
}

.conversation-nav__coin {
	width: 16px;
	height: 16px;
	flex-shrink: 0;
}

.conversation-nav__balance-text {
	min-width: 0;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
	font-size: 13px;
	font-weight: 700;
	color: #4f84ea;
}

.conversation-nav__back {
	margin-left: 6px;
	z-index: 2;
	transition: opacity 0.12s ease;
}

.conversation-nav__back:active {
	opacity: 0.58;
}

.conversation-nav__back-icon {
	font-size: 30px;
	color: #0f172a;
	line-height: 1;
	transform: translateY(-1px);
}

.conversation-nav__title {
	position: absolute;
	left: 0;
	right: 0;
	text-align: center;
	font-size: 17px;
	font-weight: 600;
	color: #0f172a;
	line-height: 1;
	pointer-events: none;
}

.conversation-nav__tabs {
	flex: 1;
	min-width: 0;
	height: 100%;
	white-space: nowrap;
}

.conversation-nav__tab-list {
	height: 100%;
	padding-left: 12px;
	display: inline-flex;
	align-items: center;
	gap: 20px;
	box-sizing: border-box;
}

.conversation-nav__tab {
	position: relative;
	height: 100%;
	padding: 0 1px;
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
	box-sizing: border-box;
	transition: opacity 0.12s ease;
}

.conversation-nav__tab:active {
	opacity: 0.68;
}

.conversation-nav__tab-text {
	font-size: 17px;
	color: #94a3b8;
	line-height: 1;
}

.conversation-nav__tab--active .conversation-nav__tab-text {
	color: #4f84ea;
	font-weight: 700;
}

.conversation-nav__tab--disabled .conversation-nav__tab-text {
	color: #cbd5e1;
}

.conversation-nav__tab-underline {
	position: absolute;
	left: 50%;
	bottom: 2px;
	width: 24px;
	height: 3px;
	border-radius: 2px;
	background: #4f84ea;
	transform: translateX(-50%);
}
</style>
