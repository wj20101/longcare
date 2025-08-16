package com.ytone.longcare.common.utils

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.ytone.longcare.navigation.navigateToHomeAndClearStack

/**
 * 统一的返回键处理工具类
 * 提供一致的返回键行为处理，确保系统返回键与导航按钮行为同步
 */
object BackHandlerUtils {
    
    /**
     * 返回行为类型
     */
    enum class BackBehavior {
        /** 返回到首页并清空堆栈 */
        HOME_AND_CLEAR_STACK,
        /** 普通返回上一页 */
        NORMAL_BACK,
        /** 自定义返回行为 */
        CUSTOM
    }
    
    /**
     * 处理返回键的通用方法
     * @param navController 导航控制器
     * @param behavior 返回行为类型
     * @param customAction 自定义返回行为（当behavior为CUSTOM时使用）
     */
    fun handleBack(
        navController: NavController,
        behavior: BackBehavior = BackBehavior.NORMAL_BACK,
        customAction: (() -> Unit)? = null
    ) {
        when (behavior) {
            BackBehavior.HOME_AND_CLEAR_STACK -> {
                navController.navigateToHomeAndClearStack()
            }
            BackBehavior.NORMAL_BACK -> {
                navController.popBackStack()
            }
            BackBehavior.CUSTOM -> {
                customAction?.invoke()
            }
        }
    }
}

/**
 * 统一的返回键处理Composable
 * 自动处理系统返回键，确保与导航按钮行为一致
 * 
 * @param navController 导航控制器
 * @param behavior 返回行为类型，默认为普通返回
 * @param customAction 自定义返回行为（当behavior为CUSTOM时使用）
 * @param enabled 是否启用返回键处理，默认为true
 */
@Composable
fun UnifiedBackHandler(
    navController: NavController,
    behavior: BackHandlerUtils.BackBehavior = BackHandlerUtils.BackBehavior.NORMAL_BACK,
    customAction: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    BackHandler(enabled = enabled) {
        BackHandlerUtils.handleBack(
            navController = navController,
            behavior = behavior,
            customAction = customAction
        )
    }
}

/**
 * 返回首页的统一处理Composable
 * 专门用于需要返回首页并清空堆栈的页面
 * 
 * @param navController 导航控制器
 * @param enabled 是否启用返回键处理，默认为true
 */
@Composable
fun HomeBackHandler(
    navController: NavController,
    enabled: Boolean = true
) {
    UnifiedBackHandler(
        navController = navController,
        behavior = BackHandlerUtils.BackBehavior.HOME_AND_CLEAR_STACK,
        enabled = enabled
    )
}

/**
 * 自定义返回行为的统一处理Composable
 * 用于需要特殊返回逻辑的页面
 * 
 * @param customAction 自定义返回行为
 * @param enabled 是否启用返回键处理，默认为true
 */
@Composable
fun CustomBackHandler(
    customAction: () -> Unit,
    enabled: Boolean = true
) {
    BackHandler(enabled = enabled) {
        customAction()
    }
}