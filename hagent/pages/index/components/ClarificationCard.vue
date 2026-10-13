<template>
	<view class="clarification-card" :class="{ 'clarification-card--inactive': !active }">
		<view class="clarification-title-row">
			<text class="clarification-title">{{ cardTitle }}</text>
			<text v-if="clarification.round && !isBalanceConfirmation" class="clarification-round">第{{ clarification.round }}次确认</text>
		</view>
		<text class="clarification-question am-selectable-text" user-select>{{ clarification.question }}</text>
		<view v-if="active && options.length" class="clarification-options">
			<view
				v-for="option in options"
				:key="option.id"
				class="clarification-option"
				:class="{ 'clarification-option--recommended': option.recommended }"
				@tap="$emit('select', option)"
			>
				<text>{{ option.label }}</text>
				<text v-if="option.recommended" class="clarification-recommended">推荐</text>
			</view>
		</view>
		<text v-if="active && clarification.allowFreeText !== false" class="clarification-tip">
			也可以直接在下方用自己的话回答
		</text>
		<view v-if="active" class="clarification-new-task" @tap="$emit('new-task')">
			<text>{{ isBalanceConfirmation ? '暂不生成' : '不回答了，开始新任务' }}</text>
		</view>
		<text v-else class="clarification-finished">该问题已处理</text>
	</view>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
	clarification: { type: Object, required: true },
	active: { type: Boolean, default: false },
})

defineEmits(['select', 'new-task'])

const options = computed(() =>
	Array.isArray(props.clarification && props.clarification.options)
		? props.clarification.options
		: []
)

const isBalanceConfirmation = computed(
	() => props.clarification && props.clarification.key === 'INSUFFICIENT_BALANCE'
)

const cardTitle = computed(() =>
	isBalanceConfirmation.value ? '豆子余额不足' : '需要确认一件事'
)
</script>

<style lang="scss" scoped>
.clarification-card {
	margin-top: 18rpx;
	padding: 28rpx;
	border-radius: 24rpx;
	border: 2rpx solid rgba(79, 132, 234, 0.24);
	background: #ffffff;
}

.clarification-card--inactive {
	opacity: 0.7;
}

.clarification-title-row {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 20rpx;
}

.clarification-title {
	font-size: 28rpx;
	font-weight: 700;
	color: #1f2937;
}

.clarification-round {
	font-size: 22rpx;
	color: #8a94a6;
}

.clarification-question {
	display: block;
	margin-top: 14rpx;
	font-size: 29rpx;
	line-height: 1.6;
	color: #273449;
}

.clarification-options {
	display: flex;
	flex-direction: column;
	gap: 14rpx;
	margin-top: 22rpx;
}

.clarification-option {
	min-height: 76rpx;
	padding: 0 24rpx;
	border-radius: 20rpx;
	background: #f0f4fa;
	color: #42526a;
	display: flex;
	align-items: center;
	justify-content: space-between;
	font-size: 27rpx;
}

.clarification-option--recommended {
	background: #edf4ff;
	color: #3778e5;
	border: 2rpx solid rgba(55, 120, 229, 0.28);
}

.clarification-recommended {
	font-size: 21rpx;
	font-weight: 700;
	padding: 4rpx 12rpx;
	border-radius: 14rpx;
	background: #4f84ea;
	color: #fff;
}

.clarification-tip,
.clarification-finished {
	display: block;
	margin-top: 18rpx;
	font-size: 23rpx;
	color: #8a94a6;
}

.clarification-new-task {
	margin-top: 20rpx;
	font-size: 24rpx;
	color: #708096;
}
</style>
