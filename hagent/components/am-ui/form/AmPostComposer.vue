<template>
	<view class="am-post-composer">
		<view class="am-post-composer__input-wrap">
			<AmMentionInput
				v-model="contentProxy"
				:topics="topics"
				:mentions="mentions"
				:maxlength="maxlength"
				:placeholder="placeholder"
			/>
			<AmButton class="am-post-composer__submit" size="medium" text="发布" :disabled="!canSubmit" @tap="emit('submit')" />
		</view>

		<AmMediaUploader
			v-model="filesProxy"
			:max-count="maxMediaCount"
			:max-visible="maxMediaVisible"
			:max-video-duration="maxVideoDuration"
			:tips="mediaTips"
			@choose="(files) => emit('mediaChoose', files)"
			@remove="(file, index) => emit('mediaRemove', file, index)"
			@preview="(file, index) => emit('mediaPreview', file, index)"
		/>

		<view class="am-post-composer__options">
			<AmComposerOption icon="#" title="添加话题" :value="topics.length ? topics.join('、') : ''" placeholder="请选择" @tap="emit('topicTap')" />
			<AmComposerOption icon="◎" title="可见范围" :value="visibility" placeholder="公开" @tap="emit('visibilityTap')" />
			<AmComposerOption icon="⌖" title="添加位置" :value="location" placeholder="选填" @tap="emit('locationTap')" />
		</view>

		<AmComposerToolbar class="am-post-composer__toolbar" :actions="toolbarActions" @action="(item) => emit('toolbarAction', item)" />
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
import AmComposerOption from './AmComposerOption.vue'
import AmComposerToolbar from './AmComposerToolbar.vue'
import AmMediaUploader from './AmMediaUploader.vue'
import AmMentionInput from './AmMentionInput.vue'

const props = defineProps({
	content: {
		type: String,
		default: '',
	},
	files: {
		type: Array,
		default: () => [],
	},
	topics: {
		type: Array,
		default: () => [],
	},
	mentions: {
		type: Array,
		default: () => [],
	},
	visibility: {
		type: String,
		default: '公开',
	},
	location: {
		type: String,
		default: '',
	},
	placeholder: {
		type: String,
		default: '分享你的想法...',
	},
	maxlength: {
		type: Number,
		default: 1000,
	},
	maxMediaCount: {
		type: Number,
		default: 9,
	},
	maxMediaVisible: {
		type: Number,
		default: 5,
	},
	maxVideoDuration: {
		type: Number,
		default: 60,
	},
	mediaTips: {
		type: String,
		default: '支持图片（最多9张）和视频（最长60秒）',
	},
	toolbarActions: {
		type: Array,
		default: () => [
			{ key: 'mention', label: '提及好友', icon: '@' },
			{ key: 'emoji', label: '表情', icon: '☺' },
			{ key: 'poll', label: '投票', icon: '▥' },
			{ key: 'activity', label: '活动', icon: '□' },
		],
	},
})

const emit = defineEmits([
	'update:content',
	'update:files',
	'submit',
	'cancel',
	'topicTap',
	'visibilityTap',
	'locationTap',
	'toolbarAction',
	'mediaChoose',
	'mediaRemove',
	'mediaPreview',
])
const contentProxy = computed({
	get: () => props.content,
	set: (value) => emit('update:content', value),
})
const filesProxy = computed({
	get: () => props.files,
	set: (value) => emit('update:files', value),
})
const canSubmit = computed(() => props.content.trim().length > 0 || props.files.length > 0)
</script>

<style lang="scss" scoped>
.am-post-composer {
	width: 100%;
	box-sizing: border-box;
	padding: 26rpx 28rpx 30rpx;
}

.am-post-composer__input-wrap {
	position: relative;
	margin-bottom: 28rpx;
}

.am-post-composer__submit {
	position: absolute;
	top: 0;
	right: 0;
	min-width: 104rpx;
}

.am-post-composer__options {
	margin-top: 28rpx;
	border-top: 1rpx solid var(--am-color-border-light);
}

.am-post-composer__toolbar {
	margin-top: 24rpx;
}

:deep(.am-mention-input__textarea) {
	min-height: 300rpx;
	padding-right: 132rpx;
	font-size: 28rpx;
	line-height: 44rpx;
}

:deep(.am-mention-input__footer) {
	margin-top: 10rpx;
}

:deep(.am-mention-input__count) {
	font-size: 24rpx;
	line-height: 34rpx;
}
</style>
