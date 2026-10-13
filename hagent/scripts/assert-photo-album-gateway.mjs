import { readdir, readFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const mobileRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const gateway = path.join(mobileRoot, 'utils/photo-album.js')
const scanRoots = ['pages', 'components', 'utils'].map((item) => path.join(mobileRoot, item))
const violations = []

async function walk(directory) {
	for (const entry of await readdir(directory, { withFileTypes: true })) {
		const target = path.join(directory, entry.name)
		if (entry.isDirectory()) {
			await walk(target)
		} else if (/\.(vue|js)$/.test(entry.name) && target !== gateway) {
			const source = await readFile(target, 'utf8')
			if (source.includes('saveImageToPhotosAlbum')) violations.push(path.relative(mobileRoot, target))
		}
	}
}

for (const root of scanRoots) await walk(root)

const gatewaySource = await readFile(gateway, 'utf8')
for (const marker of [
	'scope.writePhotosAlbum',
	'getAppAuthorizeSetting',
	'openAppAuthorizeSetting',
	'getSetting',
	'openSetting',
	'PRIVACY_NOT_DECLARED_ERRNO = 112',
	'isPrivacyNotDeclared',
	'isPrivacyRejected',
	'isSystemAlbumPermissionFailure',
]) {
	if (!gatewaySource.includes(marker)) violations.push(`utils/photo-album.js 缺少 ${marker}`)
}

if (violations.length) {
	console.error(`相册保存入口守卫失败:\n- ${violations.join('\n- ')}`)
	process.exit(1)
}

console.log('相册保存入口守卫通过')
