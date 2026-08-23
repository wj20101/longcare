package com.ytone.longcare.features.endservice.ui
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.R
import com.ytone.longcare.features.endservice.api.EndServiceSelectionActions
import com.ytone.longcare.features.endservice.vm.EndServiceSelectionUiState
import com.ytone.longcare.features.endservice.vm.EndServiceSelectionViewModel
import com.ytone.longcare.features.servicecountdown.vm.ServiceCountdownViewModel
import com.ytone.longcare.navigation.EndOderInfo
import com.ytone.longcare.theme.bgGradientBrush
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.common.utils.singleClick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EndServiceSelectionScreen(
    actions: EndServiceSelectionActions,
    orderKey: OrderKey,
    initialProjectIdList: List<Int>,
    endType: Int,
    viewModel: EndServiceSelectionViewModel = hiltViewModel(),
    countdownViewModel: ServiceCountdownViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val projectList by viewModel.projectList.collectAsStateWithLifecycle()
    val selectedProjectIds by viewModel.selectedProjectIds.collectAsStateWithLifecycle()
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()



    LaunchedEffect(Unit) {
        viewModel.initData(orderKey, initialProjectIdList)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.end_service_confirm_projects_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = singleClick { actions.onNavigateBack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }, containerColor = Color.Transparent, modifier = Modifier.background(bgGradientBrush)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val currentState = uiState) {
                is EndServiceSelectionUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
                is EndServiceSelectionUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = currentState.message,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    }
                }
                is EndServiceSelectionUiState.Success -> {
                    EndServiceSelectionSuccessContent(
                        actions = actions,
                        viewModel = viewModel,
                        countdownViewModel = countdownViewModel,
                        orderKey = orderKey,
                        endType = endType,
                        projectList = projectList,
                        selectedProjectIds = selectedProjectIds,
                        context = context,
                        scope = scope
                    )
                }
            }
        }
    }
}
