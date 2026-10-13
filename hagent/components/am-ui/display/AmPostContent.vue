<template>
	<view class="am-post-content">
		<text class="am-post-content__title" v-if="title">{{ title }}</text>
		<text class="am-post-content__text" v-if="content">{{ content }}</text>
		<view class="am-post-content__media" v-if="images.length" @tap.stop>
			<AmImageGrid :images="images" @preview="emit('preview', $event)" />
		</view>
		<view class="am-post-content__media" v-if="videoCover" @tap.stop>
			<AmVideoCard :cover="videoCover" :duration="videoDuration" @tap="emit('videoTap')" />
		</view>
		<view class="am-post-content__topics" v-if="topics.length">
			<AmTag v-for="topic in topics" :key="topic" :text="`# ${topic}`" plain />
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


import AmTag from '../base/AmTag.vue'
import AmImageGrid from './AmImageGrid.vue'
import AmVideoCard from './AmVideoCard.vue'

defineProps({
	title: {
		type: String,
		default: '',
	},
	content: {
		type: String,
		default: '',
	},
	images: {
		type: Array,
		default: () => [],
	},
	videoCover: {
		type: String,
		default: '',
	},
	videoDuration: {
		type: String,
		default: '',
	},
	topics: {
		type: Array,
		default: () => [],
	},
})

const emit = defineEmits(['preview', 'videoTap'])
</script>

<style lang="scss" scoped>
.am-post-content__title {
	display: block;
	font-size: 32rpx;
	color: var(--am-color-text-title);
	font-weight: 800;
	line-height: 46rpx;
}

.am-post-content__text {
	display: block;
	margin-top: 12rpx;
	font-size: 26rpx;
	color: var(--am-color-text-body);
	line-height: 42rpx;
}

.am-post-content__media {
	margin-top: 18rpx;
}

.am-post-content__topics {
	display: flex;
	align-items: center;
	gap: 10rpx;
	flex-wrap: wrap;
	margin-top: 18rpx;
}
</style>
