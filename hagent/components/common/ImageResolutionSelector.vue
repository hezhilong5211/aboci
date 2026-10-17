<template>
	<view class="resolution-selector" :class="{ 'resolution-selector--disabled': disabled }">
		<picker mode="selector" :range="options" range-key="label" :value="selectedIndex" :disabled="disabled || loading || !options.length" @change="select">
			<view class="resolution-heading">
				<text class="resolution-label">图片分辨率</text>
				<view class="resolution-arrow" />
			</view>
			<text class="resolution-value">{{ selected ? selected.value : (loading ? '加载中…' : '默认') }}</text>
			<text class="resolution-price">{{ selected && selected.beans != null ? `${selected.beans} 豆/张` : '按后台配置计费' }}</text>
		</picker>
	</view>
</template>
<script setup>
import { computed } from 'vue'
const props = defineProps({ options: { type: Array, default: () => [] }, modelValue: String, disabled: Boolean, loading: Boolean })
const emit = defineEmits(['update:modelValue'])
const selected = computed(() => props.options.find((option) => option.value === props.modelValue))
const selectedIndex = computed(() => Math.max(0, props.options.findIndex((option) => option.value === props.modelValue)))
function select(event) {
	if (props.disabled || props.loading) return
	const option = props.options[Number(event.detail.value)]
	if (option) emit('update:modelValue', option.value)
}
</script>
<style scoped>
.resolution-selector { padding: 12rpx 18rpx 8rpx; border: 1rpx solid #e5e9f0; border-radius: 20rpx; background: rgba(255, 255, 255, 0.7); }
.resolution-heading { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; }
.resolution-label { color: #778397; font-size: 20rpx; line-height: 28rpx; }
.resolution-arrow { width: 9rpx; height: 9rpx; margin-right: 3rpx; border-right: 2rpx solid #8b97a7; border-bottom: 2rpx solid #8b97a7; transform: rotate(45deg) translateY(-3rpx); flex-shrink: 0; }
.resolution-value { display: block; color: #344966; font-size: 24rpx; font-weight: 500; line-height: 36rpx; }
.resolution-price { display: block; min-height: 36rpx; color: #7b8799; font-size: 20rpx; line-height: 30rpx; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.resolution-selector--disabled { opacity: 0.5; }
</style>
