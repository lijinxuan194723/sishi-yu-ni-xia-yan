package cn.sishiyuni.core.model

/** A display request is not a measurement of achieved frame rate. */
data class DisplayModeSpec(val id: Int, val width: Int, val height: Int, val hz: Float)

object RefreshRatePolicy {
    fun choose(current: DisplayModeSpec, available: List<DisplayModeSpec>): Int {
        if (current.width <= 0 || current.height <= 0) return 0
        return available.filter {
            it.id > 0 && it.width == current.width && it.height == current.height && it.hz.isFinite() && it.hz > 0f
        }.sortedWith(compareByDescending<DisplayModeSpec> { it.hz }
            .thenByDescending { it.id == current.id }.thenBy { it.id }).firstOrNull()?.id ?: 0
    }
}
