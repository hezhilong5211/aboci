import { watch, type App, type Directive, type DirectiveBinding, type WatchStopHandle } from 'vue';
import { usePermissionStore } from '@/stores/permission';

interface PermissionBindingState {
  required: string[];
  stop: WatchStopHandle;
}

const bindingStates = new WeakMap<HTMLElement, PermissionBindingState>();

function normalizeRequired(binding: DirectiveBinding): string[] {
  const value = binding.value;
  if (!Array.isArray(value) || value.length === 0) {
    console.error("v-hasPermi 需传入权限数组,如 v-hasPermi=\"['skill:add']\"");
    return [];
  }
  return value.filter((item): item is string => typeof item === 'string' && item.length > 0);
}

function applyPermission(el: HTMLElement, state: PermissionBindingState) {
  const permissionStore = usePermissionStore();
  // 配置错误也按无权限处理，避免误把敏感操作暴露出来。
  el.hidden = state.required.length === 0 || !permissionStore.hasAnyPermission(state.required);
}

/**
 * v-hasPermi:按钮级权限控制。无权限则把元素从 DOM 移除。
 * 用法:v-hasPermi="['skill:add']"(数组内任一权限命中即显示);超管(*:*:*)放行。
 */
const hasPermi: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding) {
    const permissionStore = usePermissionStore();
    const state = {
      required: normalizeRequired(binding),
      stop: (() => undefined) as WatchStopHandle,
    };
    state.stop = watch(
      () => [permissionStore.permissionsReady, ...permissionStore.permissions],
      () => applyPermission(el, state),
      { immediate: true }
    );
    bindingStates.set(el, state);
  },
  updated(el: HTMLElement, binding: DirectiveBinding) {
    const state = bindingStates.get(el);
    if (!state) return;
    state.required = normalizeRequired(binding);
    applyPermission(el, state);
  },
  unmounted(el: HTMLElement) {
    const state = bindingStates.get(el);
    state?.stop();
    bindingStates.delete(el);
  },
};

/** 注册全部自定义指令 */
export function setupDirectives(app: App) {
  app.directive('hasPermi', hasPermi);
}

export default hasPermi;
