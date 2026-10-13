<template>
	<view class="page">
		<view class="nav" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav-inner">
				<view class="nav-back" @tap="goBack">‹</view>
				<text class="nav-title">推广用户</text>
				<view class="nav-space" />
			</view>
		</view>
		<scroll-view class="scroll" scroll-y @scrolltolower="loadMore">
			<view class="content" :style="{ paddingTop: (statusBarHeight + 58) + 'px' }">
				<view v-for="item in records" :key="item.userId" class="user-card">
					<view class="user-head">
						<view class="user-identity">
							<image class="user-avatar" :src="avatarDisplay(item.avatarUrl)" mode="aspectFill" />
							<view class="user-info">
								<text class="user-name">{{ item.nickName || '微信用户' }}</text>
								<text class="user-phone">{{ item.phoneMasked || '未绑定手机' }}</text>
							</view>
						</view>
						<text class="bind-time">绑定 {{ formatTime(item.bindTime) }}</text>
					</view>
					<view class="metrics">
						<view class="metric"><text>{{ item.orderCount || 0 }}</text><text>订单数</text></view>
						<view class="metric"><text>¥{{ money(item.orderAmount) }}</text><text>订单金额</text></view>
						<view class="metric"><text>¥{{ money(item.commissionAmount) }}</text><text>佣金</text></view>
					</view>
				</view>
				<view v-if="!loading && records.length === 0" class="empty">暂无有效推广用户</view>
				<view v-else class="footer">{{ loading ? '加载中...' : (hasMore ? '上拉加载更多' : '没有更多了') }}</view>
			</view>
		</scroll-view>
	</view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getDistributionUsers } from '@/api/distribution.js'
import config from '@/utils/config.js'

const statusBarHeight = ref(44)
const records = ref([])
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)
const loading = ref(false)
const hasMore = computed(() => records.value.length < total.value)
const info = typeof uni.getWindowInfo === 'function' ? uni.getWindowInfo() : uni.getSystemInfoSync()
statusBarHeight.value = info.statusBarHeight || 44

onLoad(loadMore)

async function loadMore() {
	if (loading.value || (pageNum.value > 1 && !hasMore.value)) return
	loading.value = true
	try {
		const result = await getDistributionUsers({ pageNum: pageNum.value, pageSize })
		records.value.push(...(result?.records || []))
		total.value = Number(result?.total) || 0
		pageNum.value += 1
	} finally {
		loading.value = false
	}
}
function money(value) { return ((Number(value) || 0) / 100).toFixed(2) }
function formatTime(value) { return value ? String(value).replace('T', ' ').slice(0, 16) : '--' }
function avatarDisplay(value) {
	if (!value) return '/static/default-avatar.png'
	return /^https?:\/\//.test(value) ? value : config.baseUrl + value
}
function goBack() { uni.navigateBack() }
</script>

<style lang="scss" scoped>
.page { min-height: 100vh; background: #f5f6fa; color: #172033; }
.nav { position: fixed; z-index: 10; top: 0; left: 0; right: 0; background: #fff; }
.nav-inner { height: 104rpx; padding: 0 28rpx; display: flex; align-items: center; }
.nav-back, .nav-space { width: 100rpx; }
.nav-back { color: #4f84ea; font-size: 60rpx; line-height: 1; }
.nav-title { flex: 1; text-align: center; font-size: 34rpx; font-weight: 650; }
.scroll { height: 100vh; }
.content { padding-left: 28rpx; padding-right: 28rpx; padding-bottom: 50rpx; }
.user-card { margin-bottom: 20rpx; padding: 30rpx; border-radius: 26rpx; background: #fff; }
.user-head { display: flex; justify-content: space-between; align-items: flex-start; }
.user-identity { display: flex; align-items: center; min-width: 0; }
.user-avatar { width: 80rpx; height: 80rpx; flex-shrink: 0; border-radius: 50%; background: #eef1f7; }
.user-info { min-width: 0; margin-left: 18rpx; }
.user-name, .user-phone { display: block; }
.user-name { max-width: 260rpx; overflow: hidden; font-size: 29rpx; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.user-phone, .bind-time { margin-top: 8rpx; color: #9299a8; font-size: 22rpx; }
.bind-time { flex-shrink: 0; margin-left: 16rpx; text-align: right; }
.metrics { display: grid; grid-template-columns: repeat(3, 1fr); margin-top: 26rpx; padding-top: 24rpx; border-top: 2rpx solid #f0f2f6; }
.metric { text-align: center; }
.metric text { display: block; font-size: 27rpx; font-weight: 600; }
.metric text:last-child { margin-top: 8rpx; color: #9299a8; font-size: 21rpx; font-weight: 400; }
.empty, .footer { padding: 100rpx 0; text-align: center; color: #9aa1af; font-size: 25rpx; }
.footer { padding: 28rpx 0; }
</style>
