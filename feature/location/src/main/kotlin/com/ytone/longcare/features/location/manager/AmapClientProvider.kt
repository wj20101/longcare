package com.ytone.longcare.features.location.manager

/** Creates a caller-owned handle. A handle need not own a separate native SDK client. */
interface AmapClientProvider {
    fun create(apiKey: String): AmapClientHandle
}
