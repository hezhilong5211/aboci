<template>
	<view class="model-selector" :class="{ 'model-selector--compact': compact, 'model-selector--disabled': disabled }">
		<view class="model-selector-body">
			<picker v-if="models.length" class="model-selector-picker" mode="selector" :range="publicOptions" range-key="displayName" :value="selectedIndex" :disabled="disabled || loading" @change="select">
				<view class="model-selector-trigger" hover-class="model-selector-trigger--pressed">
					<view class="model-selector-heading">
						<text class="model-selector-label">{{ label }}</text>
						<view class="model-selector-arrow" />
					</view>
					<text class="model-selector-name">{{ displayName }}</text>
				</view>
			</picker>
			<view v-else class="model-selector-trigger">
				<text class="model-selector-label">{{ label }}</text>
				<text class="model-selector-name model-selector-empty">{{ loading ? '档位加载中…' : '暂无可用档位' }}</text>
			</view>
			<view v-if="selected" class="model-selector-fee" hover-class="model-selector-fee--pressed" role="button" :aria-label="`${label}计费说明`" @tap.stop="showPricing">
				<text class="model-selector-price">{{ compact ? pricingSummary : pricing }}</text>
				<text class="model-selector-info">i</text>
			</view>
		</view>
	</view>
</template>
<script setup>
import { computed } from 'vue'
const props = defineProps({ label: String, models: { type: Array, default: () => [] }, modelValue: [String, Number], disabled: Boolean, loading: Boolean, compact: Boolean })
const emit = defineEmits(['update:modelValue'])
const selectedIndex = computed(() => Math.max(0, props.models.findIndex((model) => String(model.modelId) === String(props.modelValue))))
const selected = computed(() => props.models.find((model) => String(model.modelId) === String(props.modelValue)))
// Only public tiers may reach the UI, including when an older server returns internal names.
function publicName(model) {
	const tiers = ['轻', '中', '高']
	if (tiers.includes(model.displayTier)) return model.displayTier
	if (tiers.includes(model.modelName)) return model.modelName
	return '中'
}
const publicOptions = computed(() => props.models.map((model) => ({ displayName: publicName(model) })))
const displayName = computed(() => selected.value ? publicName(selected.value) : '请选择档位')
const imageCosts = computed(() => Object.entries((selected.value && selected.value.beanCosts) || {})
	.filter(([, beans]) => beans != null && String(beans).trim() !== '' && Number.isFinite(Number(beans)) && Number(beans) >= 0))
const pricingSummary = computed(() => {
	if (!selected.value) return ''
	if (selected.value.modelType === 'chat') return '按用量计费'
	const costs = imageCosts.value.map(([, beans]) => Number(beans))
	if (!costs.length) return '价格未配置'
	const lowest = Math.min(...costs)
	return `${lowest} 豆/张${costs.some((cost) => cost !== lowest) ? '起' : ''}`
})
const pricing = computed(() => {
	const m = selected.value
	if (!m) return ''
	if (m.modelType === 'chat') {
		const input = m.beanPerMtokenInput == null ? 0 : m.beanPerMtokenInput
		const output = m.beanPerMtokenOutput == null ? 0 : m.beanPerMtokenOutput
		const cached = m.beanPerMtokenInputCached == null ? '' : ` · 缓存输入 ${m.beanPerMtokenInputCached}`
		return `每百万 token：输入 ${input} · 输出 ${output}${cached} 豆；按实际用量合计向上取整`
	}
	const costs = imageCosts.value.map(([size, beans]) => `${size} ${beans} 豆/张`)
	return costs.length ? costs.join(' · ') : '价格未配置，暂不可生成'
})
function showPricing() {
	if (!selected.value) return
	uni.showModal({
		title: `${props.label}计费说明`,
		content: `${displayName.value}\n\n${pricing.value}`,
		showCancel: false,
		confirmText: '知道了',
		confirmColor: '#4f76ad',
	})
}
function select(event) {
	if (props.disabled || props.loading) return
	const model = props.models[Number(event.detail.value)]
	if (model) emit('update:modelValue', model.modelId)
}
</script>
<style scoped>
.model-selector { min-width: 0; padding: 10rpx 4rpx; }
.model-selector-body { min-width: 0; }
.model-selector-picker { display: block; min-width: 0; }
.model-selector-trigger { min-width: 0; }
.model-selector-heading { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; }
.model-selector-label { display: block; color: #778397; font-size: 20rpx; line-height: 28rpx; }
.model-selector-name { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #344966; font-size: 24rpx; font-weight: 500; line-height: 36rpx; }
.model-selector-arrow { width: 9rpx; height: 9rpx; margin-right: 3rpx; border-right: 2rpx solid #8b97a7; border-bottom: 2rpx solid #8b97a7; transform: rotate(45deg) translateY(-3rpx); flex-shrink: 0; }
.model-selector-fee { display: flex; align-items: center; gap: 8rpx; min-height: 36rpx; }
.model-selector-price { min-width: 0; color: #7b8799; font-size: 20rpx; line-height: 30rpx; }
.model-selector-info { display: flex; align-items: center; justify-content: center; flex-shrink: 0; width: 21rpx; height: 21rpx; border: 1rpx solid #a5afbd; border-radius: 50%; color: #8b97a7; font-size: 16rpx; font-weight: 500; line-height: 1; }
.model-selector-empty { color: #929cab; font-weight: 400; }
.model-selector--compact { padding: 0; }
.model-selector--compact .model-selector-body { padding: 12rpx 18rpx 8rpx; border: 1rpx solid #e5e9f0; border-radius: 20rpx; background: rgba(255, 255, 255, 0.7); }
.model-selector--compact .model-selector-price { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.model-selector--disabled .model-selector-trigger { opacity: 0.5; }
.model-selector-trigger--pressed, .model-selector-fee--pressed { opacity: 0.65; }
</style>
