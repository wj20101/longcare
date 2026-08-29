package com.ytone.longcare.domain.userstorage

import com.ytone.longcare.model.NamespaceId
import java.nio.charset.StandardCharsets

data class UserTaskIdentity(
    val namespaceId: NamespaceId,
    val sessionEpoch: SessionEpoch,
    val taskType: String,
    val businessId: String,
) {
    init {
        require(taskType.isNotBlank()) { "taskType must not be blank" }
        require(businessId.isNotBlank()) { "businessId must not be blank" }
    }

    fun encode(): String = buildString {
        append("longcare-task-v1|")
        append(namespaceId.value)
        append('|')
        append(sessionEpoch.value)
        append('|')
        appendLengthPrefixed(taskType)
        append('|')
        appendLengthPrefixed(businessId)
    }
}

private fun StringBuilder.appendLengthPrefixed(value: String) {
    append(value.toByteArray(StandardCharsets.UTF_8).size)
    append(':')
    append(value)
}
