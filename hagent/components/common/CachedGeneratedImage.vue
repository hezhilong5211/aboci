<template>
	<image
		class="cached-generated-image"
		:class="{ 'is-width-fix': mode === 'widthFix' }"
		:src="displaySrc"
		:mode="mode"
		:lazy-load="lazyLoad"
		@tap="emit('tap', $event)"
		@longpress="emit('longpress', $event)"
		@load="emit('load', $event)"
		@error="handleError"
	/>
</template>

<script setup>
import { getCurrentInstance, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import {
	findGeneratedImageCache,
	invalidateGeneratedImage,
	resolveGeneratedImage,
} from '@/utils/generated-image-cache.js'

const props = defineProps({
	src: { type: String, default: '' },
	fallbackSrc: { type: String, default: '' },
	userId: { type: [String, Number], default: '' },
	cacheType: { type: String, default: 'preview' },
	mode: { type: String, default: 'aspectFill' },
	lazyLoad: { type: Boolean, default: true },
})

const emit = defineEmits(['tap', 'longpress', 'load', 'error'])
const displaySrc = ref('')
const instance = getCurrentInstance()
let requestVersion = 0
let fallbackUsed = false
let observer = null

function disconnectObserver() {
	if (observer) observer.disconnect()
	observer = null
}

async function downloadVisibleSource(source, version) {
	const resolved = await resolveGeneratedImage(source, {
		userId: props.userId,
		type: props.cacheType,
	})
	if (version === requestVersion) displaySrc.value = resolved || source
}

function observeSource(source, version) {
	nextTick(() => {
		if (version !== requestVersion) return
		if (!instance || !instance.proxy || typeof instance.proxy.createIntersectionObserver !== 'function') {
			downloadVisibleSource(source, version)
			return
		}
		observer = instance.proxy.createIntersectionObserver({ nativeMode: true })
		observer.relativeToViewport({ top: 300, bottom: 300 }).observe('.cached-generated-image', (res) => {
			if (!res || res.intersectionRatio <= 0) return
			disconnectObserver()
			downloadVisibleSource(source, version)
		})
	})
}

async function loadSource() {
	const version = ++requestVersion
	disconnectObserver()
	fallbackUsed = false
	displaySrc.value = ''
	const source = props.src
	if (!source) return
	const cached = await findGeneratedImageCache(source, {
		userId: props.userId,
		type: props.cacheType,
	})
	if (version !== requestVersion) return
	if (cached) {
		displaySrc.value = cached
		return
	}
	observeSource(source, version)
}

async function handleError(event) {
	emit('error', event)
	if (displaySrc.value && displaySrc.value !== props.src) {
		await invalidateGeneratedImage(props.src, {
			userId: props.userId,
			type: props.cacheType,
		})
		displaySrc.value = props.src
		return
	}
	if (!fallbackUsed && props.fallbackSrc && props.fallbackSrc !== props.src) {
		fallbackUsed = true
		displaySrc.value = props.fallbackSrc
	}
}

watch(
	() => [props.src, props.userId, props.cacheType],
	loadSource,
	{ immediate: true }
)

onBeforeUnmount(() => {
	requestVersion += 1
	disconnectObserver()
})
</script>

<style scoped>
.cached-generated-image {
	display: block;
	width: 100%;
	height: 100%;
}

.cached-generated-image.is-width-fix {
	height: auto;
}
</style>
