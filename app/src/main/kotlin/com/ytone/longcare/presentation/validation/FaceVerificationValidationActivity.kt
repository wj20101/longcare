package com.ytone.longcare.presentation.validation

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ytone.longcare.R
import com.ytone.longcare.features.identification.domain.CheckFaceOrderIdPolicy
import com.ytone.longcare.navigation.IdentificationRoute
import com.ytone.longcare.navigation.LocalFaceImageMetricsReporter
import com.ytone.longcare.navigation.OrderNavParams
import com.ytone.longcare.navigation.registerAppNavGraphs
import com.ytone.longcare.theme.LongCareTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.Serializable

@AndroidEntryPoint
class FaceVerificationValidationActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialOrderId =
            intent.getLongExtra(EXTRA_ORDER_ID, INVALID_ORDER_ID)
                .takeIf(CheckFaceOrderIdPolicy::isSupported)

        setContent {
            LongCareTheme {
                val navController = rememberNavController()
                var initialOrderHandled by rememberSaveable { mutableStateOf(false) }
                val openValidation = { orderId: Long ->
                    navController.navigate(
                        IdentificationRoute(OrderNavParams(orderId = orderId)),
                    )
                }

                CompositionLocalProvider(
                    LocalFaceImageMetricsReporter provides { metrics ->
                        val sizeKiB = metrics.byteCount / BYTES_PER_KIBIBYTE
                        Toast.makeText(
                            this@FaceVerificationValidationActivity,
                            getString(
                                R.string.face_validation_photo_metrics,
                                metrics.widthPx,
                                metrics.heightPx,
                                sizeKiB,
                                metrics.byteCount,
                            ),
                            Toast.LENGTH_LONG,
                        ).show()
                    },
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = FaceVerificationValidationEntryRoute,
                    ) {
                        composable<FaceVerificationValidationEntryRoute> {
                            FaceVerificationValidationEntryScreen(
                                onClose = ::finish,
                                onOpenFaceVerification = openValidation,
                            )
                        }
                        registerAppNavGraphs(navController)
                    }
                }

                LaunchedEffect(initialOrderId, initialOrderHandled) {
                    if (initialOrderId != null && !initialOrderHandled) {
                        initialOrderHandled = true
                        openValidation(initialOrderId)
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_ORDER_ID = "com.ytone.longcare.validation.extra.ORDER_ID"
        private const val INVALID_ORDER_ID = 0L
        private const val BYTES_PER_KIBIBYTE = 1024.0
    }
}

@Serializable
private data object FaceVerificationValidationEntryRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FaceVerificationValidationEntryScreen(
    onClose: () -> Unit,
    onOpenFaceVerification: (Long) -> Unit,
) {
    var orderIdText by rememberSaveable { mutableStateOf("") }
    var orderIdErrorRes by rememberSaveable { mutableStateOf<Int?>(null) }

    val submit = {
        val orderId = orderIdText.toLongOrNull()
        when {
            orderId == null || orderId <= 0L -> {
                orderIdErrorRes = R.string.face_validation_invalid_order_id
            }

            !CheckFaceOrderIdPolicy.isSupported(orderId) -> {
                orderIdErrorRes = R.string.face_validation_order_id_out_of_range
            }

            else -> {
                orderIdErrorRes = null
                onOpenFaceVerification(orderId)
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.face_validation_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.face_validation_close),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(R.string.face_validation_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = orderIdText,
                onValueChange = { value ->
                    orderIdText = value.filter(Char::isDigit)
                    orderIdErrorRes = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.face_validation_order_id)) },
                supportingText =
                    if (orderIdErrorRes != null) {
                        { Text(stringResource(checkNotNull(orderIdErrorRes))) }
                    } else {
                        null
                    },
                isError = orderIdErrorRes != null,
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            Button(
                onClick = submit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.face_validation_open))
            }
        }
    }
}
