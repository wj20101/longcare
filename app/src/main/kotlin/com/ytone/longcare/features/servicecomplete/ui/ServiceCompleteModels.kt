package com.ytone.longcare.features.servicecomplete.ui

import com.ytone.longcare.model.ServiceProjectM

data class ServiceSummary(
    val clientName: String,
    val clientAge: Int,
    val clientIdNumber: String,
    val clientAddress: String,
    val serviceContent: String,
    val duration: String
)

fun getSelectedProjectsContentByIds(
    allProjects: List<ServiceProjectM>,
    selectedProjectIds: List<Int>
): String {
    return if (selectedProjectIds.isNotEmpty()) {
        allProjects
            .filter { project -> selectedProjectIds.contains(project.projectId) }
            .joinToString(", ") { it.projectName }
    } else {
        allProjects.joinToString(", ") { it.projectName }
    }
}

fun formatSelectedProjectsDurationByIds(
    allProjects: List<ServiceProjectM>,
    selectedProjectIds: List<Int>
): String {
    val totalMinutes = if (selectedProjectIds.isNotEmpty()) {
        allProjects
            .filter { project -> selectedProjectIds.contains(project.projectId) }
            .sumOf { it.serviceTime }
    } else {
        allProjects.sumOf { it.serviceTime }
    }
    return formatServiceDuration(totalMinutes)
}

fun formatServiceDuration(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}小时${minutes}分钟"
        hours > 0 -> "${hours}小时"
        minutes > 0 -> "${minutes}分钟"
        else -> "0分钟"
    }
}
