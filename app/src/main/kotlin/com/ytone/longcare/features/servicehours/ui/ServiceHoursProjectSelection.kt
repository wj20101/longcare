package com.ytone.longcare.features.servicehours.ui

import com.ytone.longcare.model.ServiceProjectM

/**
 * 获取选中的项目列表
 * @param allProjects 所有项目列表
 * @param selectedProjectIds 选中的项目ID列表
 * @return 过滤后的项目列表
 */
internal fun getSelectedProjects(
    allProjects: List<ServiceProjectM>,
    selectedProjectIds: List<Int>
): List<ServiceProjectM> {
    return if (selectedProjectIds.isNotEmpty()) {
        allProjects.filter { project ->
            selectedProjectIds.contains(project.projectId)
        }
    } else {
        allProjects
    }
}
