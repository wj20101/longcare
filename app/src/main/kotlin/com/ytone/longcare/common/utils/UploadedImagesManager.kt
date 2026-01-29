package com.ytone.longcare.common.utils

import android.content.SharedPreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.ytone.longcare.api.request.OrderInfoRequestModel
import com.ytone.longcare.di.OrderStorage
import com.ytone.longcare.features.photoupload.model.ImageTaskType
import com.ytone.longcare.features.photoupload.model.ImageTask
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

/**
 * 已上传图片数据管理器
 * 用于管理已上传图片的本地存储，与订单关联，支持页面恢复时数据可用
 */
@Singleton
class UploadedImagesManager @Inject constructor(
    @param:OrderStorage private val sharedPreferences: SharedPreferences,
    private val moshi: Moshi
) {
    companion object {
        private const val KEY_UPLOADED_IMAGES = "uploaded_images_"
    }

    /**
     * 保存已上传的图片数据到本地存储
     * @param orderRequest 订单信息请求模型
     * @param images 图片数据，按任务类型分组
     */
    fun saveUploadedImages(
        orderRequest: OrderInfoRequestModel,
        images: Map<ImageTaskType, List<ImageTask>>
    ) {
        val key = getUploadedImagesKey(orderRequest.orderId)
        val type = Types.newParameterizedType(
            Map::class.java, ImageTaskType::class.java,
            Types.newParameterizedType(List::class.java, ImageTask::class.java)
        )
        val adapter = moshi.adapter<Map<ImageTaskType, List<ImageTask>>>(type)
        val json = adapter.toJson(images)
        sharedPreferences.edit {
            putString(key, json)
        }
    }

    /**
     * 获取已上传的图片数据
     * @param orderRequest 订单信息请求模型
     * @return 图片数据，按任务类型分组
     */
    fun getUploadedImages(orderRequest: OrderInfoRequestModel): Map<ImageTaskType, List<ImageTask>> {
        val key = getUploadedImagesKey(orderRequest.orderId)
        val json = sharedPreferences.getString(key, null) ?: return emptyMap()

        return try {
            val type = Types.newParameterizedType(
                Map::class.java, ImageTaskType::class.java,
                Types.newParameterizedType(List::class.java, ImageTask::class.java)
            )
            val adapter = moshi.adapter<Map<ImageTaskType, List<ImageTask>>>(type)
            adapter.fromJson(json) ?: emptyMap()
        } catch (e: Exception) {
            // JSON解析失败时返回空Map
            emptyMap()
        }
    }

    /**
     * 删除已上传的图片数据
     * @param orderRequest 订单信息请求模型
     */
    fun deleteUploadedImages(orderRequest: OrderInfoRequestModel) {
        val key = getUploadedImagesKey(orderRequest.orderId)
        sharedPreferences.edit {
            remove(key)
        }
    }

    /**
     * 清除所有已上传图片数据（用于清理或重置）
     */
    fun clearAllUploadedImages() {
        sharedPreferences.edit {
            val allKeys = sharedPreferences.all.keys
            allKeys.filter { it.startsWith(KEY_UPLOADED_IMAGES) }.forEach { key ->
                remove(key)
            }
        }
    }

    /**
     * 检查是否存在已上传的图片数据
     * @param orderRequest 订单信息请求模型
     * @return 是否存在图片数据
     */
    fun hasUploadedImages(orderRequest: OrderInfoRequestModel): Boolean {
        val uploadedImages = getUploadedImages(orderRequest)
        return uploadedImages.isNotEmpty() && uploadedImages.values.any { it.isNotEmpty() }
    }

    /**
     * 获取存储键
     * @param orderId 订单ID
     * @return 存储键
     */
    private fun getUploadedImagesKey(orderId: Long): String {
        return "${KEY_UPLOADED_IMAGES}${orderId}"
    }
}