/**
 * 将扁平数组转换为树形结构
 * @param data 扁平数组数据
 * @param id 主键字段名，默认为 'id'
 * @param pid 父级字段名，默认为 'parentId'
 * @param children 子节点字段名，默认为 'children'
 * @param rootId 根节点的父级ID值，默认为 0
 * @returns 树形结构数组
 */
export function handleTree<T extends Record<string, any>>(
  data: T[],
  id: string = 'id',
  pid: string = 'parentId',
  children: string = 'children',
  rootId: number | string = 0
): T[] {
  if (!data || data.length === 0) {
    return []
  }

  // 创建ID映射表，深拷贝避免修改原数据
  const idMap = new Map<any, T>()
  data.forEach(item => {
    idMap.set(item[id], { ...item, [children]: [] })
  })

  // 构建树形结构
  const tree: T[] = []
  data.forEach(item => {
    const node = idMap.get(item[id])!
    const parentId = item[pid]
    
    // 判断是否为根节点
    if (parentId === rootId || parentId === null || parentId === undefined) {
      tree.push(node)
    } else {
      // 查找父节点
      const parent = idMap.get(parentId)
      if (parent) {
        if (!parent[children]) {
          parent[children] = []
        }
        parent[children].push(node)
      } else {
        // 如果找不到父节点，也作为根节点处理（可能是数据不完整）
        tree.push(node)
      }
    }
  })

  // 递归排序（按 orderNum 排序）
  const sortTree = (nodes: T[]): T[] => {
    return nodes
      .sort((a, b) => {
        const aOrder = a.orderNum ?? a.order_num ?? 0
        const bOrder = b.orderNum ?? b.order_num ?? 0
        return aOrder - bOrder
      })
      .map(node => {
        if (node[children] && node[children].length > 0) {
          node[children] = sortTree(node[children])
        }
        return node
      })
  }

  return sortTree(tree)
}

