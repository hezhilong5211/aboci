<template>
	<view class="am-radio-group" :class="`am-radio-group--${direction}`">
		<AmRadio
			v-for="item in options"
			:key="item.value"
			:label="item.label"
			:checked="modelValue === item.value"
			:disabled="disabled || item.disabled"
			@tap="handleSelect(item)"
		/>
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


import AmRadio from './AmRadio.vue'

const props = defineProps({
	modelValue: {
		type: [String, Number, Boolean],
		default: '',
	},
	options: {
		type: Array,
		default: () => [],
	},
	direction: {
		type: String,
		default: 'vertical',
	},
	disabled: {
		type: Boolean,
		default: false,
	},
})

const emit = defineEmits(['update:modelValue', 'change'])

function handleSelect(item) {
	if (props.disabled || item.disabled) return
	emit('update:modelValue', item.value)
	emit('change', item.value)
}
</script>

<style lang="scss" scoped>
.am-radio-group {
	display: flex;
	gap: 22rpx;
}

.am-radio-group--vertical {
	flex-direction: column;
}

.am-radio-group--horizontal {
	flex-direction: row;
	flex-wrap: wrap;
}
</style>
