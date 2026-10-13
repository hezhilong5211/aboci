<template>
	<view class="am-post-card" @tap="emit('tap')">
		<view @tap.stop="emit('authorTap')">
			<AmAuthorBar :avatar="post.avatar" :name="post.author" :desc="post.time" :tag="post.badge" right-text="•••" />
		</view>
		<view class="am-post-card__content">
			<AmPostContent
				:title="post.title"
				:content="post.content"
				:images="post.images || []"
				:video-cover="post.videoCover"
				:video-duration="post.videoDuration"
				:topics="post.topics || []"
				@preview="emit('preview', $event)"
				@video-tap="emit('videoTap')"
			/>
		</view>
		<view @tap.stop>
			<AmActionBar :actions="actions" @action="emit('action', $event)" />
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


import { computed } from 'vue'
import AmActionBar from './AmActionBar.vue'
import AmAuthorBar from './AmAuthorBar.vue'
import AmPostContent from './AmPostContent.vue'

const props = defineProps({
	post: {
		type: Object,
		default: () => ({}),
	},
})

const emit = defineEmits(['tap', 'authorTap', 'preview', 'videoTap', 'action'])
const actions = computed(() => [
	{ key: 'like', icon: '♥', label: '赞', count: props.post.likes || '', active: props.post.liked },
	{ key: 'comment', icon: '◎', label: '评论', count: props.post.comments || '' },
	{ key: 'star', icon: '☆', label: '收藏', count: props.post.stars || '' },
	{ key: 'share', icon: '↗', label: '分享', count: props.post.shares || '' },
])
</script>

<style lang="scss" scoped>
.am-post-card {
	padding: 24rpx;
	border-radius: 24rpx;
	background: #ffffff;
	box-shadow: var(--am-shadow-card);
	box-sizing: border-box;
}

.am-post-card__content {
	margin: 18rpx 0;
}
</style>
