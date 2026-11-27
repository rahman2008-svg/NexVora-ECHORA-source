/*
 * file:    DevTrace.kt
 * desc:    Event sink for device validation. Null (no-op) unless installed; installed only by
 *          the devtest variant (ResoundApp). Never changes control flow.
 */
package com.nexvora.echora.core

object DevTrace {
    @Volatile var sink: ((String) -> Unit)? = null
    fun e(msg: String) { sink?.invoke(msg) }
}
