<template>
	<view class="am-date-picker" :class="{ 'am-date-picker--disabled': disabled }">
		<picker
			v-if="mode === 'date'"
			mode="multiSelector"
			:range="dateRanges"
			:value="dateIndexes"
			:disabled="disabled"
			@columnchange="handleDateColumnChange"
			@change="handleDateChange"
		>
			<view class="am-date-picker__trigger">
				<view class="am-date-picker__main">
					<text class="am-date-picker__title" v-if="title">{{ title }}</text>
					<text class="am-date-picker__value" :class="{ 'am-date-picker__value--placeholder': !modelValue }">
						{{ displayValue }}
					</text>
				</view>
				<text class="am-date-picker__icon">▦</text>
			</view>
		</picker>
		<picker
			v-else
			mode="time"
			:value="modelValue"
			:start="start"
			:end="end"
			:disabled="disabled"
			@change="handleTimeChange"
		>
			<view class="am-date-picker__trigger">
				<view class="am-date-picker__main">
					<text class="am-date-picker__title" v-if="title">{{ title }}</text>
					<text class="am-date-picker__value" :class="{ 'am-date-picker__value--placeholder': !modelValue }">
						{{ displayValue }}
					</text>
				</view>
				<text class="am-date-picker__icon">◷</text>
			</view>
		</picker>
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


import { computed, ref, watch } from 'vue'

const props = defineProps({
	modelValue: {
		type: String,
		default: '',
	},
	mode: {
		type: String,
		default: 'date',
	},
	fields: {
		type: String,
		default: 'day',
	},
	start: {
		type: String,
		default: '',
	},
	end: {
		type: String,
		default: '',
	},
	title: {
		type: String,
		default: '',
	},
	placeholder: {
		type: String,
		default: '请选择日期',
	},
	disabled: {
		type: Boolean,
		default: false,
	},
})

const emit = defineEmits(['update:modelValue', 'change'])
const selectedYear = ref(2026)
const selectedMonth = ref(1)
const selectedDay = ref(1)

const startParts = computed(() => parseDate(props.start, { year: 1970, month: 1, day: 1 }))
const endParts = computed(() => parseDate(props.end, { year: 2099, month: 12, day: 31 }))
const yearOptions = computed(() => {
	const years = []
	const startYear = Math.min(startParts.value.year, endParts.value.year)
	const endYear = Math.max(startParts.value.year, endParts.value.year)
	for (let year = startYear; year <= endYear; year += 1) {
		years.push(year)
	}
	return years
})
const monthOptions = computed(() => getMonthOptions(selectedYear.value))
const dayOptions = computed(() => getDayOptions(selectedYear.value, selectedMonth.value))
const dateRanges = computed(() => {
	const ranges = [yearOptions.value.map((year) => `${year}年`)]
	if (props.fields !== 'year') {
		ranges.push(monthOptions.value.map((month) => `${pad2(month)}月`))
	}
	if (props.fields === 'day') {
		ranges.push(dayOptions.value.map((day) => `${pad2(day)}日`))
	}
	return ranges
})
const dateIndexes = computed(() => {
	const indexes = [Math.max(0, yearOptions.value.indexOf(selectedYear.value))]
	if (props.fields !== 'year') {
		indexes.push(Math.max(0, monthOptions.value.indexOf(selectedMonth.value)))
	}
	if (props.fields === 'day') {
		indexes.push(Math.max(0, dayOptions.value.indexOf(selectedDay.value)))
	}
	return indexes
})
const displayValue = computed(() => props.modelValue || placeholderWithField.value)
const placeholderWithField = computed(() => props.placeholder)

watch(
	() => [props.modelValue, props.start, props.end, props.fields],
	() => {
		const next = clampDate(parseDate(props.modelValue, getTodayParts()))
		selectedYear.value = next.year
		selectedMonth.value = next.month
		selectedDay.value = next.day
	},
	{ immediate: true }
)

function pad2(value) {
	return String(value).padStart(2, '0')
}

function getTodayParts() {
	const date = new Date()
	return {
		year: date.getFullYear(),
		month: date.getMonth() + 1,
		day: date.getDate(),
	}
}

function parseDate(value, fallback) {
	if (!value) return { ...fallback }
	const parts = String(value).split('-').map((item) => Number(item))
	const year = parts[0]
	const month = parts[1] || 1
	const day = parts[2] || 1
	if (!year || Number.isNaN(year)) return { ...fallback }
	return { year, month, day }
}

function getMonthOptions(year) {
	let start = 1
	let end = 12
	if (year === startParts.value.year) start = startParts.value.month
	if (year === endParts.value.year) end = endParts.value.month
	const options = []
	for (let month = start; month <= end; month += 1) {
		options.push(month)
	}
	return options.length ? options : [1]
}

function getDaysInMonth(year, month) {
	return new Date(year, month, 0).getDate()
}

function getDayOptions(year, month) {
	let start = 1
	let end = getDaysInMonth(year, month)
	if (year === startParts.value.year && month === startParts.value.month) start = startParts.value.day
	if (year === endParts.value.year && month === endParts.value.month) end = Math.min(end, endParts.value.day)
	const options = []
	for (let day = start; day <= end; day += 1) {
		options.push(day)
	}
	return options.length ? options : [1]
}

function clampDate(parts) {
	const years = yearOptions.value
	let year = Math.max(years[0], Math.min(years[years.length - 1], parts.year))
	let months = getMonthOptions(year)
	let month = Math.max(months[0], Math.min(months[months.length - 1], parts.month))
	let days = getDayOptions(year, month)
	let day = Math.max(days[0], Math.min(days[days.length - 1], parts.day))
	return { year, month, day }
}

function formatDate() {
	if (props.fields === 'year') return String(selectedYear.value)
	if (props.fields === 'month') return `${selectedYear.value}-${pad2(selectedMonth.value)}`
	return `${selectedYear.value}-${pad2(selectedMonth.value)}-${pad2(selectedDay.value)}`
}

function syncSelectionByIndexes(indexes) {
	const year = yearOptions.value[indexes[0]] || selectedYear.value
	const months = getMonthOptions(year)
	const monthIndex = props.fields === 'year' ? 0 : (indexes[1] || 0)
	const month = months[monthIndex] || months[0]
	const days = getDayOptions(year, month)
	const dayIndex = props.fields === 'day' ? (indexes[2] || 0) : 0
	const day = days[dayIndex] || days[0]
	const next = clampDate({ year, month, day })
	selectedYear.value = next.year
	selectedMonth.value = next.month
	selectedDay.value = next.day
}

function handleDateColumnChange(event) {
	if (props.disabled) return
	const column = event.detail.column
	const value = event.detail.value
	let next = {
		year: selectedYear.value,
		month: selectedMonth.value,
		day: selectedDay.value,
	}
	if (column === 0) {
		next.year = yearOptions.value[value] || selectedYear.value
	}
	if (column === 1) {
		const months = getMonthOptions(selectedYear.value)
		next.month = months[value] || selectedMonth.value
	}
	if (column === 2) {
		const days = getDayOptions(selectedYear.value, selectedMonth.value)
		next.day = days[value] || selectedDay.value
	}
	next = clampDate(next)
	selectedYear.value = next.year
	selectedMonth.value = next.month
	selectedDay.value = next.day
}

function handleDateChange(event) {
	if (props.disabled) return
	syncSelectionByIndexes(event.detail.value)
	const value = formatDate()
	emit('update:modelValue', value)
	emit('change', value)
}

function handleTimeChange(event) {
	if (props.disabled) return
	const value = event.detail.value
	emit('update:modelValue', value)
	emit('change', value)
}
</script>

<style lang="scss" scoped>
.am-date-picker {
	width: 100%;
}

.am-date-picker--disabled {
	opacity: 0.48;
}

.am-date-picker__trigger {
	box-sizing: border-box;
	min-height: 72rpx;
	padding: 0 22rpx;
	border: 2rpx solid var(--am-color-border);
	border-radius: var(--am-radius-control);
	background: var(--am-color-bg-surface);
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 18rpx;
}

.am-date-picker__main {
	flex: 1;
	min-width: 0;
	display: flex;
	flex-direction: column;
	justify-content: center;
}

.am-date-picker__title {
	font-size: 20rpx;
	color: var(--am-color-text-tertiary);
	line-height: 28rpx;
}

.am-date-picker__value {
	font-size: 26rpx;
	color: var(--am-color-text-title);
	line-height: 36rpx;
}

.am-date-picker__value--placeholder {
	color: var(--am-color-text-tertiary);
}

.am-date-picker__icon {
	font-size: 30rpx;
	color: var(--am-color-primary);
	line-height: 1;
}
</style>
