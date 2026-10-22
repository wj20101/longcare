package com.ytone.longcare.model

/** Process-local authentication snapshot. Null user means unresolved, changing, or logged out. */
class UserSessionSnapshot(val generation: Long, val user: User?) {
    // Credentials are only used to build the encrypted header, never to identify/log a request.
    override fun toString(): String = "UserSessionSnapshot(generation=$generation, authenticated=${user != null})"
}
