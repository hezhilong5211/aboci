<template>
	<ShowcaseShell title="表单组件" subtitle="Input / Picker / Filter / Upload">
		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="输入 Input">
				<view class="form-stack">
					<AmInput v-model="inputValue" placeholder="请输入内容" clearable />
					<AmInput v-model="focusValue" placeholder="聚焦边框为蓝色" clearable />
					<AmInput v-model="errorValue" placeholder="错误状态" error-text="请输入有效内容" />
					<AmInput model-value="不可输入内容" disabled />
				</view>
			</AmSection>
		</AmCard>

		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="搜索 Search">
				<AmSearchBar v-model="searchValue" placeholder="搜索商品、订单、用户..." show-cancel @cancel="searchValue = ''" />
			</AmSection>
		</AmCard>

		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="选择控件">
				<view class="control-row">
					<text class="control-label">开关</text>
					<AmSwitch v-model="switchValue" />
				</view>
				<view class="control-row">
					<text class="control-label">复选框</text>
					<AmCheckbox v-model="checkedValue" label="已选中" />
				</view>
				<view class="control-block">
					<text class="control-label">单选框</text>
					<AmRadioGroup v-model="radioValue" :options="radioOptions" direction="horizontal" />
				</view>
				<view class="control-row">
					<text class="control-label">步进器</text>
					<AmStepper v-model="stepperValue" :min="1" :max="9" />
				</view>
			</AmSection>
		</AmCard>

		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="滑块 Slider">
				<view class="form-stack">
					<AmSlider v-model="sliderValue" label="音量" unit="%" />
					<AmSlider v-model="disabledSliderValue" label="禁用状态" disabled />
				</view>
			</AmSection>
		</AmCard>

		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="选择器 Picker">
				<view class="form-stack">
					<AmPicker v-model="pickerValue" title="服务类型" :options="pickerOptions" placeholder="请选择服务类型" />
					<AmDatePicker v-model="dateValue" title="预约日期" start="2024-01-01" end="2028-12-31" />
					<AmDatePicker v-model="timeValue" mode="time" title="预约时间" placeholder="请选择时间" start="09:00" end="21:00" />
				</view>
			</AmSection>
		</AmCard>

		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="上传 Upload">
				<AmUpload v-model="uploadFiles" :max-count="4" tips="支持相册和拍照，组件只负责本地选择与列表展示。" />
			</AmSection>
		</AmCard>

		<AmCard class="demo-card" padding="28rpx">
			<AmSection title="筛选 FilterPanel">
				<AmFilterPanel v-model="filterValue" v-model:keyword="filterKeyword" :groups="filterGroups" />
			</AmSection>
		</AmCard>
	</ShowcaseShell>
</template>

<script setup>
import { ref } from 'vue'
import ShowcaseShell from './components/ShowcaseShell.vue'

const inputValue = ref('')
const focusValue = ref('设计系统')
const errorValue = ref('')
const searchValue = ref('')
const switchValue = ref(true)
const checkedValue = ref(true)
const radioValue = ref('design')
const stepperValue = ref(2)
const sliderValue = ref(60)
const disabledSliderValue = ref(32)
const pickerValue = ref('standard')
const dateValue = ref('2026-04-24')
const timeValue = ref('10:30')
const uploadFiles = ref([{ path: '/static/default-avatar.png' }])
const filterKeyword = ref('订单')
const filterValue = ref({ status: 'active', type: ['vip'] })

const radioOptions = [
	{ label: '设计', value: 'design' },
	{ label: '开发', value: 'dev' },
	{ label: '产品', value: 'product' },
]

const pickerOptions = [
	{ label: '标准服务', value: 'standard' },
	{ label: '加急服务', value: 'fast' },
	{ label: '专属顾问', value: 'vip' },
]

const filterGroups = [
	{
		key: 'status',
		title: '状态',
		options: [
			{ label: '全部', value: 'all' },
			{ label: '进行中', value: 'active' },
			{ label: '已完成', value: 'done' },
		],
	},
	{
		key: 'type',
		title: '类型',
		multiple: true,
		options: [
			{ label: '会员', value: 'vip' },
			{ label: '订单', value: 'order' },
			{ label: '售后', value: 'service' },
		],
	},
]
</script>

<style lang="scss" scoped>
.demo-card {
	margin-bottom: 22rpx;
}

.form-stack {
	display: flex;
	flex-direction: column;
	gap: 18rpx;
}

.control-row {
	height: 72rpx;
	display: flex;
	align-items: center;
	justify-content: space-between;
	border-bottom: 1rpx solid var(--am-color-border-light);
}

.control-row:last-child {
	border-bottom: none;
}

.control-block {
	padding: 20rpx 0;
	border-bottom: 1rpx solid var(--am-color-border-light);
	display: flex;
	flex-direction: column;
	gap: 18rpx;
}

.control-label {
	font-size: 26rpx;
	color: var(--am-color-text-body);
}
</style>
