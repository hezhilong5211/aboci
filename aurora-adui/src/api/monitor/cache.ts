import request from '@/utils/request';

/**
 * 缓存名称
 */
export interface CacheName {
  cacheName: string;
  remark: string;
}

/**
 * 缓存键名
 */
export interface CacheKey {
  cacheKey: string;
  remark: string;
}

/**
 * 缓存内容
 */
export interface CacheContent {
  cacheName: string;
  cacheKey: string;
  cacheValue: string;
  remark: string;
}

/**
 * 获取缓存名称列表
 */
export function getCacheNames() {
  return request.get<CacheName[]>('/monitor/cache/names');
}

/**
 * 获取缓存键名列表
 * @param cacheName 缓存名称
 */
export function getCacheKeys(cacheName: string) {
  return request.get<CacheKey[]>(`/monitor/cache/keys/${cacheName}`);
}

/**
 * 获取缓存内容
 * @param cacheName 缓存名称
 * @param cacheKey 缓存键名
 */
export function getCacheValue(cacheName: string, cacheKey: string) {
  return request.get<CacheContent>('/monitor/cache/value', {
    params: { cacheName, cacheKey }
  });
}

/**
 * 清除指定缓存键
 * @param cacheKey 缓存键名
 */
export function clearCacheKey(cacheKey: string) {
  return request.delete(`/monitor/cache/key/${encodeURIComponent(cacheKey)}`);
}

/**
 * 清除指定名称下的所有缓存
 * @param cacheName 缓存名称
 */
export function clearCacheName(cacheName: string) {
  return request.delete(`/monitor/cache/name/${cacheName}`);
}

/**
 * 清除所有缓存
 */
export function clearAllCache() {
  return request.delete('/monitor/cache/all');
}
