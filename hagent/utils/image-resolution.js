/** Only advertise sizes and prices provided by the selected model's public catalogue. */
export function imageResolutionOptions(model) {
	const supported = (model?.resolutions || []).map((value) => String(value).toUpperCase())
	return ['1K', '2K', '4K'].filter((value) => supported.includes(value)).map((value) => {
		const raw = model?.beanCosts?.[value]
		const beans = raw != null && String(raw).trim() !== '' && Number.isFinite(Number(raw)) && Number(raw) >= 0
			? Number(raw) : null
		return { value, beans, label: beans == null ? value : `${value} · ${beans} 豆/张` }
	})
}

export function chooseImageResolution(model, current) {
	const values = imageResolutionOptions(model).map((option) => option.value)
	const value = String(current || '').toUpperCase()
	if (values.includes(value)) return value
	const defaultValue = String(model?.defaultResolution || '').toUpperCase()
	return values.includes(defaultValue) ? defaultValue : values[0] || ''
}
