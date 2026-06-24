package com.ytone.longcare.data.database.entity

import com.ytone.longcare.model.OrderElderInfoEntity
import com.ytone.longcare.model.OrderEntity
import com.ytone.longcare.model.OrderImageEntity
import com.ytone.longcare.model.OrderLocalStateEntity
import com.ytone.longcare.model.OrderLocationEntity
import com.ytone.longcare.model.OrderProjectEntity

fun OrderEntityDb.toModel(): OrderEntity = OrderEntity(
    orderId = orderId,
    planId = planId,
    state = state,
    startTime = startTime,
    endTime = endTime,
    lastSyncTime = lastSyncTime,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun OrderEntity.toDb(): OrderEntityDb = OrderEntityDb(
    orderId = orderId,
    planId = planId,
    state = state,
    startTime = startTime,
    endTime = endTime,
    lastSyncTime = lastSyncTime,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun OrderElderInfoEntityDb.toModel(): OrderElderInfoEntity = OrderElderInfoEntity(
    orderId = orderId,
    elderUserId = elderUserId,
    elderName = elderName,
    elderIdCard = elderIdCard,
    elderAge = elderAge,
    elderGender = elderGender,
    elderAddress = elderAddress,
    elderLng = elderLng,
    elderLat = elderLat,
    lastServiceTime = lastServiceTime,
    monthServiceTime = monthServiceTime,
    monthNoServiceTime = monthNoServiceTime,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun OrderElderInfoEntity.toDb(): OrderElderInfoEntityDb = OrderElderInfoEntityDb(
    orderId = orderId,
    elderUserId = elderUserId,
    elderName = elderName,
    elderIdCard = elderIdCard,
    elderAge = elderAge,
    elderGender = elderGender,
    elderAddress = elderAddress,
    elderLng = elderLng,
    elderLat = elderLat,
    lastServiceTime = lastServiceTime,
    monthServiceTime = monthServiceTime,
    monthNoServiceTime = monthNoServiceTime,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun OrderLocalStateEntityDb.toModel(): OrderLocalStateEntity = OrderLocalStateEntity(
    orderId = orderId,
    localStatus = localStatus,
    localStartTimestamp = localStartTimestamp,
    localEndTimestamp = localEndTimestamp,
    faceVerificationCompleted = faceVerificationCompleted,
    needsSync = needsSync,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun OrderLocalStateEntity.toDb(): OrderLocalStateEntityDb = OrderLocalStateEntityDb(
    orderId = orderId,
    localStatus = localStatus,
    localStartTimestamp = localStartTimestamp,
    localEndTimestamp = localEndTimestamp,
    faceVerificationCompleted = faceVerificationCompleted,
    needsSync = needsSync,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun OrderProjectEntityDb.toModel(): OrderProjectEntity = OrderProjectEntity(
    id = id,
    orderId = orderId,
    projectId = projectId,
    projectName = projectName,
    serviceTime = serviceTime,
    lastServiceTime = lastServiceTime,
    isComplete = isComplete,
    isSelected = isSelected,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun OrderProjectEntity.toDb(): OrderProjectEntityDb = OrderProjectEntityDb(
    id = id,
    orderId = orderId,
    projectId = projectId,
    projectName = projectName,
    serviceTime = serviceTime,
    lastServiceTime = lastServiceTime,
    isComplete = isComplete,
    isSelected = isSelected,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun OrderImageEntityDb.toModel(): OrderImageEntity = OrderImageEntity(
    id = id,
    orderId = orderId,
    imageType = imageType,
    localUri = localUri,
    localPath = localPath,
    uploadStatus = uploadStatus,
    cloudKey = cloudKey,
    cloudUrl = cloudUrl,
    errorMessage = errorMessage,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun OrderImageEntity.toDb(): OrderImageEntityDb = OrderImageEntityDb(
    id = id,
    orderId = orderId,
    imageType = imageType,
    localUri = localUri,
    localPath = localPath,
    uploadStatus = uploadStatus,
    cloudKey = cloudKey,
    cloudUrl = cloudUrl,
    errorMessage = errorMessage,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun OrderLocationEntityDb.toModel(): OrderLocationEntity = OrderLocationEntity(
    id = id,
    orderId = orderId,
    latitude = latitude,
    longitude = longitude,
    accuracy = accuracy,
    provider = provider,
    coordType = coordType,
    locationType = locationType,
    trustedLevel = trustedLevel,
    locationTime = locationTime,
    uploadStatus = uploadStatus,
    timestamp = timestamp
)

fun OrderLocationEntity.toDb(): OrderLocationEntityDb = OrderLocationEntityDb(
    id = id,
    orderId = orderId,
    latitude = latitude,
    longitude = longitude,
    accuracy = accuracy,
    provider = provider,
    coordType = coordType,
    locationType = locationType,
    trustedLevel = trustedLevel,
    locationTime = locationTime,
    uploadStatus = uploadStatus,
    timestamp = timestamp
)
