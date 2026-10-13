<template>
	<AmActionSheet
		:visible="visible"
		title="内容操作"
		:actions="actions"
		cancel-text="取消"
		@close="emit('close')"
		@select="emit('select', $event)"
	/>
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


import AmActionSheet from './AmActionSheet.vue'

defineProps({
	visible: {
		type: Boolean,
		default: false,
	},
	actions: {
		type: Array,
		default: () => [
			{ label: '举报', value: 'report', type: 'danger' },
			{ label: '拉黑', value: 'block', type: 'danger' },
			{ label: '不感兴趣', value: 'dislike' },
		],
	},
})

const emit = defineEmits(['close', 'select'])
</script>
