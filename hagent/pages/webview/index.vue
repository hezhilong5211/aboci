<template>
	<view class="webview-page">
		<web-view v-if="url" :src="url" @message="handleMessage"></web-view>
		<view v-else class="error-tip">
			<text>无法加载此页面：URL 不合法或不在允许范围内</text>
		</view>
	</view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { WEBVIEW_ALLOWED_DOMAINS } from '@/config/platform'

const url = ref('')

function isUrlAllowed(rawUrl) {
	if (!rawUrl) return false

	// 只允许 https 协议
	if (!rawUrl.startsWith('https://')) return false

	try {
		const hostname = rawUrl.split('/')[2].split(':')[0]
		return WEBVIEW_ALLOWED_DOMAINS.some(domain =>
			hostname === domain || hostname.endsWith('.' + domain)
		)
	} catch {
		return false
	}
}

onLoad((options) => {
	if (options.url) {
		const decodedUrl = decodeURIComponent(options.url)
		if (isUrlAllowed(decodedUrl)) {
			url.value = decodedUrl
		} else {
			console.warn('[WebView] URL 不在白名单内:', decodedUrl)
			url.value = ''
		}
	}
})

/** 接收 H5 页面消息 */
function handleMessage(e) {
	console.log('[WebView] 收到消息:', e.detail)
}
</script>

<style scoped>
.webview-page {
	flex: 1;
}
.error-tip {
	display: flex;
	justify-content: center;
	align-items: center;
	height: 100vh;
	color: #999;
	font-size: 14px;
}
</style>
