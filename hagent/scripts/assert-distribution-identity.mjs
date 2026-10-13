import { readFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const mobileRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const read = (file) => readFile(path.join(mobileRoot, file), 'utf8')
const violations = []

const [profile, request, app, attribution] = await Promise.all([
	read('pages/user/components/ProfileContent.vue'),
	read('api/request.js'),
	read('App.vue'),
	read('utils/distribution-attribution.js'),
])

const entryStart = profile.indexOf('async function loadDistributionEntry()')
const entryEnd = profile.indexOf('// 拉取当前用户的全部展示数据', entryStart)
const entryLoader = entryStart >= 0 && entryEnd > entryStart ? profile.slice(entryStart, entryEnd) : ''
if (!entryLoader) violations.push('找不到 loadDistributionEntry')
if (entryLoader.includes('catch (e) {\n\t\tdistributionEntryVisible.value = false')) {
	violations.push('/distribution/me 请求失败仍会直接隐藏推广入口')
}

for (const marker of [
	"AUTH_EXPIRED_EVENT = 'auth-expired'",
	'uni.$emit(AUTH_EXPIRED_EVENT)',
]) {
	if (!request.includes(marker)) violations.push(`api/request.js 缺少 ${marker}`)
}
for (const marker of [
	'uni.$on(AUTH_EXPIRED_EVENT, this.handleAuthExpired)',
	'useUserStore().clearAuth()',
]) {
	if (!app.includes(marker)) violations.push(`App.vue 缺少 ${marker}`)
}
for (const marker of [
	'result?.bound || result?.alreadyBound',
	'removeStorage(PENDING_KEY)',
]) {
	if (!attribution.includes(marker)) violations.push(`utils/distribution-attribution.js 缺少 ${marker}`)
}

if (violations.length) {
	console.error(`分销身份状态守卫失败:\n- ${violations.join('\n- ')}`)
	process.exit(1)
}

console.log('分销身份状态守卫通过')
