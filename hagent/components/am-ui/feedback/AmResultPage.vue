<template>
	<view class="am-result-page">
		<view class="am-result-page__icon" :class="`am-result-page__icon--${type}`">
			<text class="am-result-page__icon-text">{{ finalIcon }}</text>
		</view>
		<text class="am-result-page__title">{{ finalTitle }}</text>
		<text class="am-result-page__desc" v-if="finalDesc">{{ finalDesc }}</text>
		<view class="am-result-page__actions" v-if="primaryText || secondaryText">
			<AmButton v-if="secondaryText" :text="secondaryText" variant="secondary" @tap="emit('secondary')" />
			<AmButton v-if="primaryText" :text="primaryText" @tap="emit('primary')" />
		</view>
	</view>
</template>

<script>
export default {
	options: {
		styleIsolation: 'shared',
		virtualHost: true
	}
}
</script>

<script setup>


import { computed } from 'vue'
import AmButton from '../base/AmButton.vue'

const presets = {
	success: { icon: '✓', title: '操作成功', desc: '您的操作已完成' },
	fail: { icon: '!', title: '操作失败', desc: '请稍后重新尝试' },
	review: { icon: '◷', title: '审核中', desc: '审核完成后会通知您' },
	network: { icon: '⌁', title: '网络异常', desc: '请检查网络连接后重试' },
	notFound: { icon: '404', title: '页面不存在', desc: '页面可能已移动或删除' },
}

const props = defineProps({
	type: {
		type: String,
		default: 'success',
	},
	icon: {
		type: String,
		default: '',
	},
	title: {
		type: String,
		default: '',
	},
	desc: {
		type: String,
		default: '',
	},
	primaryText: {
		type: String,
		default: '确认',
	},
	secondaryText: {
		type: String,
		default: '',
	},
})

const emit = defineEmits(['primary', 'secondary'])
const preset = computed(() => presets[props.type] || presets.success)
const finalIcon = computed(() => props.icon || preset.value.icon)
const finalTitle = computed(() => props.title || preset.value.title)
const finalDesc = computed(() => props.desc || preset.value.desc)
</script>

<style lang="scss" scoped>
.am-result-page {
	min-height: 640rpx;
	padding: 96rpx 48rpx;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	text-align: center;
	box-sizing: border-box;
}

.am-result-page__icon {
	min-width: 136rpx;
	height: 136rpx;
	padding: 0 26rpx;
	border-radius: 42rpx;
	background: var(--am-color-primary-weak);
	display: flex;
	align-items: center;
	justify-content: center;
	box-sizing: border-box;
}

.am-result-page__icon--success {
	background: rgba(34, 197, 94, 0.12);
}

.am-result-page__icon--fail,
.am-result-page__icon--notFound {
	background: rgba(255, 90, 95, 0.1);
}

.am-result-page__icon--review {
	background: rgba(255, 176, 32, 0.14);
}

.am-result-page__icon-text {
	font-size: 44rpx;
	color: var(--am-color-primary);
	font-weight: 800;
	line-height: 1;
}

.am-result-page__icon--success .am-result-page__icon-text {
	color: var(--am-color-success);
}

.am-result-page__icon--fail .am-result-page__icon-text,
.am-result-page__icon--notFound .am-result-page__icon-text {
	color: var(--am-color-danger);
}

.am-result-page__icon--review .am-result-page__icon-text {
	color: var(--am-color-warning);
}

.am-result-page__title {
	margin-top: 30rpx;
	font-size: 36rpx;
	color: var(--am-color-text-title);
	font-weight: 800;
	line-height: 48rpx;
}

.am-result-page__desc {
	margin-top: 12rpx;
	font-size: 24rpx;
	color: var(--am-color-text-secondary);
	line-height: 38rpx;
}

.am-result-page__actions {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 18rpx;
	margin-top: 38rpx;
}
</style>
