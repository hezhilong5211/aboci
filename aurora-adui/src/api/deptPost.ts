import request from '@/utils/request';

// ===================== 部门管理 API =====================

/**
 * 查询部门树形列表
 */
export function listDept() {
    return request.get('/system/dept/list');
}

/**
 * 查询部门列表（排除某个节点）
 */
export function listDeptExcludeChild(deptId: number) {
    return request.get(`/system/dept/list/exclude/${deptId}`);
}

/**
 * 获取部门详情
 */
export function getDept(deptId: number) {
    return request.get(`/system/dept/${deptId}`);
}

/**
 * 新增部门
 */
export function addDept(data: any) {
    return request.post('/system/dept', data);
}

/**
 * 修改部门
 */
export function updateDept(data: any) {
    return request.put('/system/dept', data);
}

/**
 * 删除部门
 */
export function deleteDept(deptId: number) {
    return request.delete(`/system/dept/${deptId}`);
}

// ===================== 岗位管理 API =====================

/**
 * 分页查询岗位列表
 */
export function listPost(params: any) {
    return request.get('/system/post/list', { params });
}

/**
 * 查询所有岗位（下拉选择用）
 */
export function listPostAll() {
    return request.get('/system/post/optionselect');
}

/**
 * 获取岗位详情
 */
export function getPost(postId: number) {
    return request.get(`/system/post/${postId}`);
}

/**
 * 新增岗位
 */
export function addPost(data: any) {
    return request.post('/system/post', data);
}

/**
 * 修改岗位
 */
export function updatePost(data: any) {
    return request.put('/system/post', data);
}

/**
 * 删除岗位
 */
export function deletePost(postId: number) {
    return request.delete(`/system/post/${postId}`);
}

/**
 * 批量删除岗位
 */
export function deletePostBatch(postIds: number[]) {
    return request.delete('/system/post/batch', { data: postIds });
}
