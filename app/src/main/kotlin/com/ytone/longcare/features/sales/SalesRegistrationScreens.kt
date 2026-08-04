package com.ytone.longcare.features.sales

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.FileProviderHelper
import com.ytone.longcare.model.LocationResult

@Composable
internal fun SalesRegistrationScreen(
    draft: SalesCustomerDraft,
    photoUris: List<Uri>,
    location: LocationResult?,
    onDraftChange: (SalesCustomerDraft) -> Unit,
    onPhotosSelected: (List<Uri>) -> Unit,
    onRemovePhoto: (Uri) -> Unit,
    onRequestLocation: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onValidationError: (String) -> Unit,
) {
    val context = LocalContext.current
    val cameraUnavailableMessage = stringResource(R.string.sales_error_camera_unavailable)
    val cameraOpenErrorMessage = stringResource(R.string.sales_error_camera_open)
    val cameraPermissionMessage = stringResource(R.string.sales_error_camera_permission)
    val validationMessage = draft.validationMessageRes()?.let { stringResource(it) }
    var showPhotoSourceSheet by rememberSaveable { mutableStateOf(false) }
    var pendingCameraUri by rememberSaveable { mutableStateOf("") }
    val currentPhotoUris by rememberUpdatedState(photoUris)
    val currentOnPhotosSelected by rememberUpdatedState(onPhotosSelected)
    val currentOnValidationError by rememberUpdatedState(onValidationError)

    val photoPicker =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.PickMultipleVisualMedia(
                    MAX_SALES_CUSTOMER_PHOTOS
                ),
            onResult = { selected ->
                currentOnPhotosSelected(
                    mergeSalesCustomerPhotoUris(currentPhotoUris, selected)
                )
            },
        )
    val cameraLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.TakePicture(),
            onResult = { success ->
                val capturedUri = pendingCameraUri.takeIf(String::isNotBlank)?.let(Uri::parse)
                pendingCameraUri = ""
                if (success && capturedUri != null) {
                    currentOnPhotosSelected(
                        mergeSalesCustomerPhotoUris(
                            existing = currentPhotoUris,
                            added = listOf(capturedUri),
                        )
                    )
                } else if (capturedUri != null) {
                    runCatching {
                        context.contentResolver.delete(capturedUri, null, null)
                    }
                }
            },
        )
    val launchCameraCapture: () -> Unit = {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            currentOnValidationError(cameraUnavailableMessage)
        } else {
            runCatching {
                val cameraUri = FileProviderHelper.createCameraPhotoUri(context)
                pendingCameraUri = cameraUri.toString()
                cameraLauncher.launch(cameraUri)
            }.onFailure {
                pendingCameraUri
                    .takeIf(String::isNotBlank)
                    ?.let(Uri::parse)
                    ?.let { uri ->
                        runCatching {
                            context.contentResolver.delete(uri, null, null)
                        }
                    }
                pendingCameraUri = ""
                currentOnValidationError(cameraOpenErrorMessage)
            }
        }
    }
    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { granted ->
                if (granted) {
                    launchCameraCapture()
                } else {
                    currentOnValidationError(cameraPermissionMessage)
                }
            },
        )
    val requestCameraCapture = {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            launchCameraCapture()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        SalesTopBar(
            title = stringResource(R.string.sales_registration_title),
            onBack = onBack,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 8.dp,
                    bottom = 20.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            item {
                SalesRegistrationField(
                    value = draft.userName,
                    placeholder = stringResource(R.string.sales_registration_name_hint),
                    onValueChange = {
                        onDraftChange(draft.copy(userName = it))
                    },
                )
            }
            item {
                SalesRegistrationField(
                    value = draft.identityCardNumber,
                    placeholder =
                        stringResource(R.string.sales_registration_identity_hint),
                    onValueChange = {
                        onDraftChange(
                            draft.copy(
                                identityCardNumber =
                                    it.uppercase().filter { char ->
                                        char.isDigit() || char == 'X'
                                    }.take(18)
                            )
                        )
                    },
                    keyboardType = KeyboardType.Ascii,
                )
            }
            item {
                SalesRegistrationField(
                    value = draft.guardianName,
                    placeholder =
                        stringResource(R.string.sales_registration_contact_hint),
                    onValueChange = {
                        onDraftChange(draft.copy(guardianName = it))
                    },
                )
            }
            item {
                SalesRegistrationField(
                    value = draft.guardianPhone,
                    placeholder =
                        stringResource(R.string.sales_registration_phone_hint),
                    onValueChange = {
                        onDraftChange(
                            draft.copy(
                                guardianPhone = it.filter(Char::isDigit).take(11)
                            )
                        )
                    },
                    keyboardType = KeyboardType.Phone,
                )
            }
            item {
                SalesRegistrationField(
                    value = draft.guardianRelation,
                    placeholder =
                        stringResource(R.string.sales_registration_relation_hint),
                    onValueChange = {
                        onDraftChange(draft.copy(guardianRelation = it))
                    },
                )
            }
            item {
                SalesRegistrationField(
                    value = draft.liveAddress,
                    placeholder =
                        stringResource(R.string.sales_registration_address_hint),
                    onValueChange = {
                        onDraftChange(draft.copy(liveAddress = it))
                    },
                    singleLine = false,
                    minHeight = 82,
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SalesPrimaryButton(
                        text =
                            if (location == null) {
                                stringResource(R.string.sales_registration_get_location)
                            } else {
                                stringResource(R.string.sales_registration_refresh_location)
                            },
                        onClick = onRequestLocation,
                        modifier = Modifier.fillMaxWidth(0.46f),
                    )
                    if (location != null) {
                        Text(
                            text =
                                stringResource(
                                    R.string.sales_registration_location_format,
                                    location.latitude,
                                    location.longitude,
                                ),
                            color = Color.White,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            item {
                Text(
                    text = stringResource(R.string.sales_registration_photos),
                    color = Color.White,
                    fontSize = 15.sp,
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    photoUris.forEach { uri ->
                        SalesPhotoThumbnail(
                            uri = uri,
                            onRemove = { onRemovePhoto(uri) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (photoUris.size < MAX_SALES_CUSTOMER_PHOTOS) {
                        SalesPhotoAddButton(
                            onClick = { showPhotoSourceSheet = true },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(
                        (MAX_SALES_CUSTOMER_PHOTOS - photoUris.size - 1)
                            .coerceAtLeast(0)
                    ) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                SalesPrimaryButton(
                    text = stringResource(R.string.sales_registration_submit),
                    onClick = {
                        if (validationMessage == null) {
                            onContinue()
                        } else {
                            onValidationError(validationMessage)
                        }
                    },
                )
            }
        }
    }

    if (showPhotoSourceSheet) {
        SalesPhotoSourceSheet(
            onDismiss = { showPhotoSourceSheet = false },
            onCamera = {
                showPhotoSourceSheet = false
                requestCameraCapture()
            },
            onGallery = {
                showPhotoSourceSheet = false
                photoPicker.launch(
                    PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SalesPhotoSourceSheet(
    onDismiss: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFFDFDFF),
        contentColor = SalesTextPrimary,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color(0xFFBBC2CC))
        },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.sales_photo_add),
                        color = SalesTextPrimary,
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.sales_photo_source_hint),
                        color = SalesTextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.sales_common_close),
                        tint = SalesTextSecondary,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFF3F7FC)),
            ) {
                SalesPhotoSourceOption(
                    icon = Icons.Rounded.PhotoCamera,
                    title = stringResource(R.string.sales_photo_camera_title),
                    description =
                        stringResource(R.string.sales_photo_camera_description),
                    onClick = onCamera,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 80.dp),
                    color = Color(0xFFDDE5EF),
                )
                SalesPhotoSourceOption(
                    icon = Icons.Rounded.PhotoLibrary,
                    title = stringResource(R.string.sales_photo_gallery_title),
                    description =
                        stringResource(R.string.sales_photo_gallery_description),
                    onClick = onGallery,
                )
            }
        }
    }
}

@Composable
private fun SalesPhotoSourceOption(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
            )
        },
        supportingContent = {
            Text(
                text = description,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        },
        leadingContent = {
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(SalesBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = SalesBlue,
                    modifier = Modifier.size(25.dp),
                )
            }
        },
        colors =
            ListItemDefaults.colors(
                containerColor = Color.Transparent,
                headlineColor = SalesTextPrimary,
                supportingColor = SalesTextSecondary,
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    role = Role.Button,
                    onClick = onClick,
                ),
    )
}

@Composable
private fun SalesRegistrationField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minHeight: Int = 50,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight.dp),
        placeholder = {
            Text(
                text = placeholder,
                color = Color(0xFFA0A3A7),
                fontSize = 15.sp,
                lineHeight = 21.sp,
            )
        },
        textStyle =
            TextStyle(
                color = SalesTextPrimary,
                fontSize = 15.sp,
                lineHeight = 21.sp,
            ),
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(12.dp),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                cursorColor = SalesBlue,
            ),
    )
}

@Composable
private fun SalesPhotoThumbnail(
    uri: Uri,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White),
    ) {
        AsyncImage(
            model = uri,
            contentDescription = stringResource(R.string.sales_photo_description),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription =
                    stringResource(R.string.sales_photo_remove_description),
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun SalesPhotoAddButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.96f))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = stringResource(R.string.sales_photo_add_description),
            tint = SalesBlue,
            modifier = Modifier.size(35.dp),
        )
    }
}

@Composable
internal fun SalesInformationConfirmationScreen(
    draft: SalesCustomerDraft,
    photoUris: List<Uri>,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        SalesTopBar(
            title = stringResource(R.string.sales_confirmation_title),
            onBack = onBack,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 10.dp,
                    bottom = 20.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .salesWhiteCard()
                            .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    SalesInfoRow(
                        stringResource(R.string.sales_customer_label_name),
                        draft.userName,
                    )
                    SalesInfoRow(
                        stringResource(R.string.sales_customer_label_age),
                        identityCardAge(draft.identityCardNumber)
                            ?.toString()
                            .orEmpty(),
                    )
                    SalesInfoRow(
                        stringResource(R.string.sales_customer_label_identity_number),
                        draft.identityCardNumber,
                    )
                    SalesInfoRow(
                        stringResource(R.string.sales_customer_label_contact),
                        draft.guardianName,
                    )
                    SalesInfoRow(
                        stringResource(R.string.sales_customer_label_phone),
                        draft.guardianPhone,
                    )
                    SalesInfoRow(
                        stringResource(R.string.sales_customer_label_relation),
                        draft.guardianRelation,
                    )
                    SalesInfoRow(
                        stringResource(R.string.sales_customer_label_address),
                        draft.liveAddress,
                    )
                    if (photoUris.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            photoUris.forEach { uri ->
                                AsyncImage(
                                    model = uri,
                                    contentDescription =
                                        stringResource(R.string.sales_photo_description),
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .aspectRatio(1.2f)
                                            .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                            repeat(
                                (MAX_SALES_CUSTOMER_PHOTOS - photoUris.size)
                                    .coerceAtLeast(0)
                            ) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            item {
                SalesPrimaryButton(
                    text = stringResource(R.string.sales_confirmation_submit),
                    onClick = onSubmit,
                )
            }
        }
    }
}

@Composable
internal fun SalesSubmitSuccessScreen(
    onBack: () -> Unit,
    onEvaluation: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        SalesTopBar(
            title = stringResource(R.string.sales_submission_result_title),
            onBack = onBack,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 10.dp,
                    bottom = 20.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(34.dp),
        ) {
            item {
                SalesSuccessPanel(
                    title = stringResource(R.string.sales_submission_success)
                )
            }
            item {
                if (useSalesLargeTextLayout()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SalesOutlinedActionButton(
                            text =
                                stringResource(R.string.sales_submission_confirm_return),
                            onClick = onBack,
                        )
                        SalesPrimaryButton(
                            text =
                                stringResource(
                                    R.string.sales_submission_start_evaluation
                                ),
                            onClick = onEvaluation,
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SalesOutlinedActionButton(
                            text =
                                stringResource(R.string.sales_submission_confirm_return),
                            onClick = onBack,
                            modifier = Modifier.weight(1f),
                        )
                        SalesPrimaryButton(
                            text =
                                stringResource(
                                    R.string.sales_submission_start_evaluation
                                ),
                            onClick = onEvaluation,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SalesEvaluationChoiceScreen(
    onBack: () -> Unit,
    onAutomaticEvaluation: () -> Unit,
    onFormEvaluation: () -> Unit,
) {
    val useLargeTextLayout = useSalesLargeTextLayout()
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        SalesTopBar(
            title = stringResource(R.string.sales_submission_result_title),
            onBack = onBack,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 10.dp,
                    bottom = 20.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (useLargeTextLayout) {
                item {
                    SalesAutomaticEvaluationChoiceCard(
                        onClick = onAutomaticEvaluation,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    SalesFormEvaluationChoiceCard(
                        onClick = onFormEvaluation,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SalesAutomaticEvaluationChoiceCard(
                            onClick = onAutomaticEvaluation,
                            modifier = Modifier.weight(1f),
                        )
                        SalesFormEvaluationChoiceCard(
                            onClick = onFormEvaluation,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SalesAutomaticEvaluationChoiceCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SalesEvaluationChoiceCard(
        title = stringResource(R.string.sales_evaluation_automatic_title),
        subtitle = stringResource(R.string.sales_evaluation_automatic_subtitle),
        onClick = onClick,
        modifier = modifier,
    ) {
        Image(
            painter = painterResource(R.drawable.sales_qlz_device_design),
            contentDescription =
                stringResource(R.string.sales_evaluation_device_image_description),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(64.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun SalesFormEvaluationChoiceCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SalesEvaluationChoiceCard(
        title = stringResource(R.string.sales_evaluation_form_title),
        subtitle = stringResource(R.string.sales_evaluation_form_subtitle),
        onClick = onClick,
        modifier = modifier,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.Assignment,
            contentDescription = null,
            tint = Color(0xFFFFC47B),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(58.dp),
        )
    }
}

@Composable
private fun SalesEvaluationChoiceCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    illustration: @Composable () -> Unit,
) {
    Column(
        modifier =
            modifier
                .heightIn(min = 128.dp)
                .salesWhiteCard()
                .clickable(onClick = onClick)
                .padding(start = 16.dp, end = 10.dp, top = 14.dp, bottom = 10.dp),
    ) {
        Text(
            text = title,
            color = SalesTextPrimary,
            fontSize = 17.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = subtitle,
            color = SalesTextSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 2,
        )
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            illustration()
        }
    }
}
