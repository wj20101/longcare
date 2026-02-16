package com.ytone.longcare.features.userlist.api

data class UserListActions(
    val onNavigateBack: () -> Unit,
    val onNavigateToUserServiceRecord: (Long, String, String) -> Unit
)
