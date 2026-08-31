package com.ytone.longcare.domain.system

/**
 * Supplies the company name for the active user session.
 *
 * Implementations own persistence, caching, and user-scope isolation. Callers must not infer
 * storage details from an empty or unavailable value.
 */
interface CompanyNameProvider {
    /** Returns the current user's cached or lazily loaded company name, or an empty value. */
    suspend fun getCompanyName(): String

    /** Refreshes the current user's company name, or returns null when it cannot be refreshed. */
    suspend fun refreshCompanyName(): String?
}
