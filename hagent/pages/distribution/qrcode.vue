<template>
	<view class="page">
		<view class="nav" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav-inner">
				<view class="nav-back" @tap="goBack">‹</view>
				<text class="nav-title">我的推广二维码</text>
				<view class="nav-space" />
			</view>
		</view>

		<view class="content" :style="{ paddingTop: (statusBarHeight + 70) + 'px' }">
			<view class="qr-card">
				<text class="card-title">邀请好友扫码进入</text>
				<view class="qr-wrap" @tap="preview">
					<image v-if="qrcode.imageData" class="qr-image" :src="qrcode.imageData" mode="aspectFit" />
					<view v-else class="qr-empty">{{ qrcode.status === 'DISABLED' ? '推广已停用' : (loading ? '生成中...' : '暂无二维码') }}</view>
				</view>
				<text class="qr-hint">二维码仅包含随机邀请码，不会暴露您的用户信息</text>
			</view>

			<view class="stats-card">
				<view class="stat-item">
					<text class="stat-value">{{ qrcode.visitCount || 0 }}</text>
					<text class="stat-label">二维码访问次数</text>
				</view>
				<view class="stat-line" />
				<view class="stat-item">
					<text class="stat-value">{{ qrcode.promotedUserCount || 0 }}</text>
					<text class="stat-label">有效绑定用户</text>
				</view>
			</view>
			<text class="stats-tip">访问次数不等于有效用户，以完成首次永久绑定为准。</text>

			<view class="actions">
				<view class="action secondary" :class="{ disabled: !qrcode.imageData || saving }" @tap="save">{{ saving ? '保存中…' : '保存相册' }}</view>
				<view class="action primary" :class="{ disabled: !qrcode.imageData }" @tap="shareImage">分享图片</view>
			</view>
		</view>
	</view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getDistributionQrcode } from '@/api/distribution.js'
import { saveImageToPhotoAlbum } from '@/utils/photo-album.js'

const statusBarHeight = ref(44)
const loading = ref(false)
const saving = ref(false)
const qrcode = reactive({ imageData: '', status: 'ACTIVE', visitCount: 0, promotedUserCount: 0 })
const info = typeof uni.getWindowInfo === 'function' ? uni.getWindowInfo() : uni.getSystemInfoSync()
statusBarHeight.value = info.statusBarHeight || 44

onLoad(loadQrcode)

async function loadQrcode() {
	loading.value = true
	try {
		Object.assign(qrcode, await getDistributionQrcode())
	} finally {
		loading.value = false
	}
}

function goBack() {
	uni.navigateBack()
}
async function preview() {
	if (!qrcode.imageData) return
	try {
		const path = await createTempFile()
		uni.previewImage({ urls: [path], current: path })
	} catch (e) {
		uni.previewImage({ urls: [qrcode.imageData], current: qrcode.imageData })
	}
}

function createTempFile() {
	return new Promise((resolve, reject) => {
		if (!qrcode.imageData) {
			reject(new Error('二维码不存在'))
			return
		}
		// #ifdef MP-WEIXIN
		const base64 = qrcode.imageData.replace(/^data:image\/\w+;base64,/, '')
		const filePath = `${wx.env.USER_DATA_PATH}/distribution-qrcode.png`
		wx.getFileSystemManager().writeFile({ filePath, data: base64, encoding: 'base64', success: () => resolve(filePath), fail: reject })
		// #endif
		// #ifndef MP-WEIXIN
		reject(new Error('当前平台不支持保存'))
		// #endif
	})
}

async function save() {
	if (!qrcode.imageData || saving.value) return
	saving.value = true
	let filePath
	try {
		filePath = await createTempFile()
	} catch (error) {
		console.warn('[DistributionQrcode] create temp file failed', error)
		uni.showToast({ title: '二维码文件生成失败，请重试', icon: 'none' })
		saving.value = false
		return
	}
	try {
		const saved = await saveImageToPhotoAlbum(filePath)
		if (saved) uni.showToast({ title: '已保存到相册', icon: 'success' })
	} catch (error) {
		console.warn('[DistributionQrcode] save image failed', error)
		uni.showToast({ title: '保存失败，请稍后重试', icon: 'none' })
	} finally {
		saving.value = false
	}
}

async function shareImage() {
	if (!qrcode.imageData) return
	try {
		const path = await createTempFile()
		// #ifdef MP-WEIXIN
		if (typeof wx.showShareImageMenu === 'function') {
			wx.showShareImageMenu({ path, fail: () => preview() })
			return
		}
		// #endif
		preview()
	} catch (e) {
		preview()
	}
}
</script>

<style lang="scss" scoped>
.page { min-height: 100vh; background: #f5f6fa; color: #172033; }
.nav { position: fixed; z-index: 10; top: 0; left: 0; right: 0; background: #fff; }
.nav-inner { height: 104rpx; padding: 0 28rpx; display: flex; align-items: center; }
.nav-back, .nav-space { width: 100rpx; }
.nav-back { color: #4f84ea; font-size: 60rpx; line-height: 1; }
.nav-title { flex: 1; text-align: center; font-size: 34rpx; font-weight: 650; }
.content { padding-left: 32rpx; padding-right: 32rpx; }
.qr-card { padding: 42rpx 34rpx; border-radius: 32rpx; text-align: center; background: #fff; }
.card-title { display: block; font-size: 32rpx; font-weight: 650; }
.qr-wrap { width: 460rpx; height: 460rpx; margin: 38rpx auto 0; display: flex; align-items: center; justify-content: center; border: 2rpx solid #e9edf5; border-radius: 24rpx; background: #fbfcff; }
.qr-image { width: 420rpx; height: 420rpx; }
.qr-empty { color: #9aa1af; font-size: 28rpx; }
.qr-hint { display: block; margin-top: 28rpx; color: #8a93a5; font-size: 23rpx; line-height: 1.6; }
.stats-card { margin-top: 24rpx; padding: 30rpx; border-radius: 28rpx; background: #fff; display: flex; align-items: center; }
.stat-item { flex: 1; text-align: center; }
.stat-value, .stat-label { display: block; }
.stat-value { font-size: 38rpx; font-weight: 700; color: #4f75d8; }
.stat-label { margin-top: 10rpx; color: #7f8797; font-size: 23rpx; }
.stat-line { width: 2rpx; height: 68rpx; background: #edf0f5; }
.stats-tip { display: block; padding: 20rpx 10rpx; color: #9299a8; font-size: 22rpx; line-height: 1.5; }
.actions { display: flex; gap: 20rpx; margin-top: 18rpx; }
.action { flex: 1; height: 92rpx; border-radius: 46rpx; display: flex; align-items: center; justify-content: center; font-size: 28rpx; font-weight: 600; }
.secondary { color: #4f75d8; background: #eaf1ff; }
.primary { color: #fff; background: linear-gradient(135deg, #4f84ea, #8178f2); }
.disabled { opacity: .45; }
</style>
