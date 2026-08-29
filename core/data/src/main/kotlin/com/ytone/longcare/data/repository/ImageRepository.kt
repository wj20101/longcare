package com.ytone.longcare.data.repository

import android.net.Uri
import com.ytone.longcare.data.database.entity.OrderImageEntityDb
import com.ytone.longcare.data.database.entity.toDb
import com.ytone.longcare.data.database.entity.toModel
import com.ytone.longcare.data.userstorage.UserDatabaseAccess
import com.ytone.longcare.data.userstorage.UserManagedFiles
import com.ytone.longcare.domain.repository.OrderImageRepository
import com.ytone.longcare.domain.userstorage.UserStorageLease
import com.ytone.longcare.model.ImageType
import com.ytone.longcare.model.ImageUploadStatus
import com.ytone.longcare.model.OrderImageEntity
import com.ytone.longcare.model.OrderKey
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** User-scoped order image records backed by relative managed-file handles. */
@Singleton
class ImageRepository @Inject constructor(
    private val databaseAccess: UserDatabaseAccess,
    private val managedFiles: UserManagedFiles,
) : OrderImageRepository {
    override suspend fun getImagesByOrderId(orderKey: OrderKey): List<OrderImageEntity> =
        databaseAccess.withCurrentLease { database, lease ->
            database.orderImageDao().getImagesByOrderId(orderKey.orderId).map { it.toModel(lease) }
        }

    fun observeImagesByOrderId(orderKey: OrderKey): Flow<List<OrderImageEntity>> =
        databaseAccess.observeCurrent { database, lease ->
            database.orderImageDao().observeImagesByOrderId(orderKey.orderId).map { records ->
                records.map { it.toModel(lease) }
            }
        }

    suspend fun getImagesByType(orderKey: OrderKey, imageType: ImageType): List<OrderImageEntity> =
        databaseAccess.withCurrentLease { database, lease ->
            database.orderImageDao().getImagesByType(orderKey.orderId, imageType.value).map { it.toModel(lease) }
        }

    fun observeImagesByType(orderKey: OrderKey, imageType: ImageType): Flow<List<OrderImageEntity>> =
        databaseAccess.observeCurrent { database, lease ->
            database.orderImageDao().observeImagesByType(orderKey.orderId, imageType.value).map { records ->
                records.map { it.toModel(lease) }
            }
        }

    suspend fun getPendingImages(orderKey: OrderKey): List<OrderImageEntity> =
        databaseAccess.withCurrentLease { database, lease ->
            database.orderImageDao()
                .getImagesByStatus(orderKey.orderId, ImageUploadStatus.PENDING.value)
                .map { it.toModel(lease) }
        }

    suspend fun getAllPendingImages(): List<OrderImageEntity> =
        databaseAccess.withCurrentLease { database, lease ->
            database.orderImageDao().getAllImagesByStatus(ImageUploadStatus.PENDING.value).map { it.toModel(lease) }
        }

    suspend fun getFailedImages(orderKey: OrderKey): List<OrderImageEntity> =
        databaseAccess.withCurrentLease { database, lease ->
            database.orderImageDao()
                .getImagesByStatus(orderKey.orderId, ImageUploadStatus.FAILED.value)
                .map { it.toModel(lease) }
        }

    override suspend fun addImage(
        orderKey: OrderKey,
        imageType: ImageType,
        localUri: String,
        localPath: String?,
    ): OrderImageEntity {
        val lease = databaseAccess.currentLease()
        val sourceFile = localPath
            ?.let(::File)
            ?.takeIf(File::isFile)
            ?: Uri.parse(localUri).path
                ?.takeIf { path -> Uri.parse(localUri).scheme in setOf(null, "file") }
                ?.let(::File)
            ?: throw IllegalArgumentException("Order image source must be a managed file")
        val source = Uri.fromFile(managedFiles.requireCurrentSessionFile(lease, sourceFile))
        val handle = managedFiles.importPersistentFile(
            lease = lease,
            purpose = IMAGE_PURPOSE,
            relativePath = "orders/${orderKey.orderId}/${UUID.randomUUID()}${source.safeExtension()}",
            source = source,
        )
        var stored = false
        return try {
            val result = databaseAccess.withLease(lease) { database, activeLease ->
                val record = OrderImageEntity(
                    orderId = orderKey.orderId,
                    imageType = imageType.value,
                    localUri = handle.value,
                    localPath = handle.value,
                    uploadStatus = ImageUploadStatus.PENDING.value,
                )
                val id = database.orderImageDao().insert(record.toDb())
                val file = managedFiles.resolvePersistentFile(activeLease, handle)
                record.copy(
                    id = id,
                    localUri = Uri.fromFile(file).toString(),
                    localPath = file.path,
                )
            }
            stored = true
            result
        } finally {
            if (!stored) {
                withContext(NonCancellable) { managedFiles.rollbackImportedFile(lease, handle) }
            }
        }
    }

    suspend fun addImages(
        orderKey: OrderKey,
        imageType: ImageType,
        localUris: List<String>,
    ) {
        localUris.forEach { uri -> addImage(orderKey, imageType, uri) }
    }

    suspend fun markAsUploading(imageId: Long) = updateStatus(imageId, ImageUploadStatus.UPLOADING)

    override suspend fun markAsSuccess(imageId: Long, cloudKey: String) {
        databaseAccess.withCurrentLease { database, _ ->
            database.orderImageDao().updateUploadSuccess(
                id = imageId,
                status = ImageUploadStatus.SUCCESS.value,
                cloudKey = cloudKey,
            )
        }
    }

    suspend fun markAsFailed(imageId: Long, errorMessage: String) {
        databaseAccess.withCurrentLease { database, _ ->
            database.orderImageDao().updateUploadFailed(
                id = imageId,
                status = ImageUploadStatus.FAILED.value,
                errorMessage = errorMessage,
            )
        }
    }

    suspend fun resetToPending(imageId: Long) = updateStatus(imageId, ImageUploadStatus.PENDING)

    suspend fun updateStatus(imageId: Long, status: ImageUploadStatus) {
        databaseAccess.withCurrentLease { database, _ ->
            database.orderImageDao().updateStatus(imageId, status.value)
        }
    }

    override suspend fun deleteImage(imageId: Long) {
        databaseAccess.withCurrentLease { database, lease ->
            val record = database.orderImageDao().getById(imageId)
            database.orderImageDao().deleteById(imageId)
            record?.let { managedFiles.deletePersistentFiles(lease, listOf(it.persistentHandle())) }
        }
    }

    override suspend fun deleteImagesByOrderId(orderKey: OrderKey) {
        databaseAccess.withCurrentLease { database, lease ->
            val records = database.orderImageDao().getImagesByOrderId(orderKey.orderId)
            database.orderImageDao().deleteByOrderId(orderKey.orderId)
            managedFiles.deletePersistentFiles(lease, records.map { it.persistentHandle() })
        }
    }

    suspend fun deleteImagesByType(orderKey: OrderKey, imageType: ImageType) {
        databaseAccess.withCurrentLease { database, lease ->
            val records = database.orderImageDao().getImagesByType(orderKey.orderId, imageType.value)
            database.orderImageDao().deleteByType(orderKey.orderId, imageType.value)
            managedFiles.deletePersistentFiles(lease, records.map { it.persistentHandle() })
        }
    }

    suspend fun countPendingImages(orderKey: OrderKey): Int =
        countByStatus(orderKey, ImageUploadStatus.PENDING)

    suspend fun countSuccessImages(orderKey: OrderKey): Int =
        countByStatus(orderKey, ImageUploadStatus.SUCCESS)

    suspend fun countFailedImages(orderKey: OrderKey): Int =
        countByStatus(orderKey, ImageUploadStatus.FAILED)

    private suspend fun countByStatus(orderKey: OrderKey, status: ImageUploadStatus): Int =
        databaseAccess.withCurrentLease { database, _ ->
            database.orderImageDao().countByStatus(orderKey.orderId, status.value)
        }

    private fun OrderImageEntityDb.toModel(lease: UserStorageLease): OrderImageEntity {
        val file = managedFiles.resolvePersistentFile(lease, persistentHandle())
        return toModel().copy(
            localUri = Uri.fromFile(file).toString(),
            localPath = file.path,
        )
    }

    private fun OrderImageEntityDb.persistentHandle() =
        UserManagedFiles.PersistentHandle(localPath ?: localUri)

    private fun Uri.safeExtension(): String {
        val extension = path.orEmpty().substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
            .takeIf { it.matches(Regex("[a-z0-9]{1,10}")) }
        return extension?.let { ".$it" }.orEmpty()
    }

    private companion object {
        const val IMAGE_PURPOSE = "order_images"
    }
}
