import request from '@/utils/request'

// ==================== 用户设置 API ====================

// 上传头像
export function uploadAvatar(file: File) {
  const formData = new FormData();
  formData.append('file', file);

  return request({
    url: '/system/user/uploadAvatar',
    method: 'post',
    data: formData,
    // 不设置 Content-Type，让浏览器自动设置 multipart/form-data 并添加 boundary
  })
}

// ==================== 菜单管理 API ====================

// 获取菜单列表
export function listMenu(params?: any) {
  return request({
    url: '/system/menu/list',
    method: 'get',
    params
  })
}

// 获取菜单树（用于下拉选择）
export function listMenuTree() {
  return request({
    url: '/system/menu/treeselect',
    method: 'get'
  })
}

// 获取菜单详情
export function getMenu(menuId: number) {
  return request({
    url: `/system/menu/${menuId}`,
    method: 'get'
  })
}

// 新增菜单
export function addMenu(data: any) {
  return request({
    url: '/system/menu',
    method: 'post',
    data
  })
}

// 修改菜单
export function updateMenu(data: any) {
  return request({
    url: '/system/menu',
    method: 'put',
    data
  })
}

// 删除菜单
export function deleteMenu(menuId: number) {
  return request({
    url: `/system/menu/${menuId}`,
    method: 'delete'
  })
}

// ==================== 角色管理 API ====================

// 获取角色列表
export function listRole(params?: any): Promise<any> {
  return request({
    url: '/system/role/list',
    method: 'get',
    params
  })
}

// 获取角色详情
export function getRole(roleId: number) {
  return request({
    url: `/system/role/${roleId}`,
    method: 'get'
  })
}

// 新增角色
export function addRole(data: any) {
  return request({
    url: '/system/role',
    method: 'post',
    data
  })
}

// 修改角色
export function updateRole(data: any) {
  return request({
    url: '/system/role',
    method: 'put',
    data
  })
}

// 删除角色（支持批量删除）
export function deleteRole(roleIds: number | number[]) {
  const ids = Array.isArray(roleIds) ? roleIds.join(',') : roleIds;
  return request({
    url: `/system/role/${ids}`,
    method: 'delete'
  })
}

// 获取角色的菜单ID列表
export function getRoleMenuIds(roleId: number) {
  return request({
    url: `/system/role/${roleId}/menuIds`,
    method: 'get'
  })
}

// ==================== 用户管理 API ====================

// 获取用户列表（分页）
// 显式标注 Promise<any>：响应拦截器已 return res.data 扁平化业务数据，
// 否则 TS 仍按 AxiosResponse 推断，调用方访问 records / total 会报"属性不存在"
export function listUser(params?: any): Promise<any> {
  return request({
    url: '/system/user/list',
    method: 'get',
    params
  })
}

// 获取用户详情
export function getUser(userId: number) {
  return request({
    url: `/system/user/${userId}`,
    method: 'get'
  })
}

// 新增用户
export function addUser(data: any) {
  return request({
    url: '/system/user',
    method: 'post',
    data
  })
}

// 修改用户
export function updateUser(data: any) {
  return request({
    url: '/system/user',
    method: 'put',
    data
  })
}

// 获取用户的角色ID列表
export function getUserRoleIds(userId: number) {
  return request({
    url: `/system/user/${userId}/roleIds`,
    method: 'get'
  })
}

// 删除用户
export function deleteUser(userIds: number | number[]) {
  const ids = Array.isArray(userIds) ? userIds.join(',') : userIds;
  return request({
    url: `/system/user/${ids}`,
    method: 'delete'
  })
}

// 重置密码
export function resetUserPwd(data: any) {
  return request({
    url: '/system/user/resetPwd',
    method: 'put',
    data
  })
}

// 修改用户状态
export function changeUserStatus(data: any) {
  return request({
    url: '/system/user/changeStatus',
    method: 'put',
    data
  })
}

// 修改当前用户密码
export function updatePassword(data: { oldPassword: string; newPassword: string }) {
  return request({
    url: '/system/user/updatePassword',
    method: 'put',
    data
  })
}

// ==================== 字典管理 API ====================

// 获取字典类型列表
export function listType(params?: any) {
  return request({
    url: '/system/dict/type/list',
    method: 'get',
    params
  })
}

// 获取字典类型详情
export function getType(dictId: number) {
  return request({
    url: `/system/dict/type/${dictId}`,
    method: 'get'
  })
}

// 新增字典类型
export function addType(data: any) {
  return request({
    url: '/system/dict/type',
    method: 'post',
    data
  })
}

// 修改字典类型
export function updateType(data: any) {
  return request({
    url: '/system/dict/type',
    method: 'put',
    data
  })
}

// 删除字典类型（支持批量删除）
export function deleteType(dictIds: number | number[]) {
  const ids = Array.isArray(dictIds) ? dictIds.join(',') : dictIds;
  return request({
    url: `/system/dict/type/${ids}`,
    method: 'delete'
  })
}

// ==================== 参数设置 API ====================

// 获取参数列表
export function listConfig(params?: any) {
  return request({
    url: '/system/config/list',
    method: 'get',
    params
  })
}

// 获取参数详情
export function getConfig(configId: number) {
  return request({
    url: `/system/config/${configId}`,
    method: 'get'
  })
}

// 新增参数
export function addConfig(data: any) {
  return request({
    url: '/system/config',
    method: 'post',
    data
  })
}

// 修改参数
export function updateConfig(data: any) {
  return request({
    url: '/system/config',
    method: 'put',
    data
  })
}

// 删除参数（支持批量删除）
export function deleteConfig(configIds: number | number[]) {
  const ids = Array.isArray(configIds) ? configIds.join(',') : configIds;
  return request({
    url: `/system/config/${ids}`,
    method: 'delete'
  })
}

// ==================== 自定义图标 API ====================

// 上传图标
export function uploadIcon(file: File, iconName?: string) {
  const formData = new FormData();
  formData.append('file', file);
  if (iconName) {
    formData.append('iconName', iconName);
  }
  return request({
    url: '/system/icon/upload',
    method: 'post',
    data: formData,
  })
}

// 获取自定义图标列表
export function listIcons() {
  return request({
    url: '/system/icon/list',
    method: 'get'
  })
}

// 删除图标
export function deleteIcon(iconId: number) {
  return request({
    url: `/system/icon/${iconId}`,
    method: 'delete'
  })
}

// 获取下一个自动生成的图标名称
export function getNextIconName() {
  return request({
    url: '/system/icon/nextName',
    method: 'get'
  })
}


