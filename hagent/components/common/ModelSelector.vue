<template>
	<view class="model-selector">
		<view class="model-selector-row">
			<text class="model-selector-label">{{ label }}</text>
			<picker v-if="models.length" mode="selector" :range="models" range-key="modelName" :value="selectedIndex" :disabled="disabled" @change="select">
				<view class="model-selector-choice">{{ selected ? selected.modelName : '请选择模型' }} <text class="model-selector-arrow">⌄</text></view>
			</picker>
			<text v-else class="model-selector-empty">{{ loading ? '模型加载中…' : '暂无可用模型' }}</text>
		</view>
		<text v-if="selected" class="model-selector-price">{{ pricing }}</text>
	</view>
</template>
<script setup>
import { computed } from 'vue'
const props = defineProps({ label: String, models: { type: Array, default: () => [] }, modelValue: [String, Number], disabled: Boolean, loading: Boolean })
const emit = defineEmits(['update:modelValue'])
const selectedIndex = computed(() => Math.max(0, props.models.findIndex((model) => String(model.modelId) === String(props.modelValue))))
const selected = computed(() => props.models.find((model) => String(model.modelId) === String(props.modelValue)))
const pricing = computed(() => {
	const m = selected.value
	if (!m) return ''
	if (m.modelType === 'chat') {
		const input = m.beanPerMtokenInput == null ? 0 : m.beanPerMtokenInput
		const output = m.beanPerMtokenOutput == null ? 0 : m.beanPerMtokenOutput
		const cached = m.beanPerMtokenInputCached == null ? '' : ` · 缓存输入 ${m.beanPerMtokenInputCached}`
		return `每百万 token：输入 ${input} · 输出 ${output}${cached} 豆；按实际用量合计向上取整`
	}
	const costs = Object.entries(m.beanCosts || {}).map(([size, beans]) => `${size} ${beans} 豆/张`)
	return costs.length ? costs.join(' · ') : '价格未配置，暂不可生成'
})
function select(event) { const model = props.models[Number(event.detail.value)]; if (model) emit('update:modelValue', model.modelId) }
</script>
<style scoped>
.model-selector { padding: 10rpx 4rpx; }
.model-selector-row { display: flex; align-items: center; gap: 16rpx; }
.model-selector-label { color: #8792a5; font-size: 22rpx; flex-shrink: 0; }
.model-selector-choice { color: #344966; font-size: 24rpx; font-weight: 500; }
.model-selector-arrow { margin-left: 8rpx; color: #8b97a7; }
.model-selector-price, .model-selector-empty { display: block; font-size: 20rpx; line-height: 1.5; color: #8b97a7; margin-top: 5rpx; }
</style>
