<template>
	<view class="am-error-state">
		<view class="am-error-state__icon" :class="`am-error-state__icon--${type}`">
			<text class="am-error-state__icon-text">{{ finalIcon }}</text>
		</view>
		<text class="am-error-state__title">{{ finalTitle }}</text>
		<text class="am-error-state__desc" v-if="finalDesc">{{ finalDesc }}</text>
		<AmButton v-if="buttonText" class="am-error-state__button" :text="buttonText" @tap="emit('action')" />
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
	network: { icon: '⌁', title: '网络异常', desc: '请检查网络后重试' },
	empty: { icon: '∅', title: '暂无内容', desc: '当前没有可展示的数据' },
	fail: { icon: '!', title: '操作失败', desc: '请稍后重新尝试' },
	review: { icon: '◷', title: '审核中', desc: '内容正在审核，请耐心等待' },
	notFound: { icon: '404', title: '页面不存在', desc: '页面可能已移动或删除' },
}

const props = defineProps({
	type: {
		type: String,
		default: 'network',
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
	buttonText: {
		type: String,
		default: '',
	},
})

const emit = defineEmits(['action'])
const preset = computed(() => presets[props.type] || presets.network)
const finalIcon = computed(() => props.icon || preset.value.icon)
const finalTitle = computed(() => props.title || preset.value.title)
const finalDesc = computed(() => props.desc || preset.value.desc)
</script>

<style lang="scss" scoped>
.am-error-state {
	padding: 54rpx 32rpx;
	display: flex;
	flex-direction: column;
	align-items: center;
	text-align: center;
}

.am-error-state__icon {
	min-width: 120rpx;
	height: 120rpx;
	padding: 0 22rpx;
	border-radius: 36rpx;
	background: var(--am-color-primary-weak);
	display: flex;
	align-items: center;
	justify-content: center;
	box-sizing: border-box;
}

.am-error-state__icon--fail {
	background: #fff0f1;
}

.am-error-state__icon--review {
	background: #fff7e6;
}

.am-error-state__icon-text {
	font-size: 38rpx;
	color: var(--am-color-primary);
	font-weight: 800;
	line-height: 1;
}

.am-error-state__icon--fail .am-error-state__icon-text {
	color: var(--am-color-danger);
}

.am-error-state__icon--review .am-error-state__icon-text {
	color: var(--am-color-warning);
}

.am-error-state__title {
	margin-top: 26rpx;
	font-size: 32rpx;
	color: var(--am-color-text-title);
	font-weight: 700;
	line-height: 44rpx;
}

.am-error-state__desc {
	margin-top: 10rpx;
	font-size: 24rpx;
	color: var(--am-color-text-secondary);
	line-height: 36rpx;
}

.am-error-state__button {
	margin-top: 30rpx;
}
</style>
