/**
 * DIY 组件注册映射表
 * 后续开发 DIY 组件时在此注册
 * DiyRenderer 根据 type 字段查找对应组件
 */

// import DiyBanner from './DiyBanner.vue'
// import DiyGrid from './DiyGrid.vue'
// import DiyNotice from './DiyNotice.vue'
// import DiyImage from './DiyImage.vue'
// import DiyRichText from './DiyRichText.vue'
// import DiySpacer from './DiySpacer.vue'

/**
 * 组件类型映射
 * key = JSON配置中的 type 字段
 * value = 对应的 Vue 组件
 */
const componentMap = {
	// banner: DiyBanner,
	// grid: DiyGrid,
	// notice: DiyNotice,
	// image: DiyImage,
	// richtext: DiyRichText,
	// spacer: DiySpacer,
}

export default componentMap
