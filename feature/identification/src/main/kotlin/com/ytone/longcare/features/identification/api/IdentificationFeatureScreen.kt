package com.ytone.longcare.features.identification.api

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ytone.longcare.features.identification.ui.IdentificationRouteScreen
import com.ytone.longcare.features.identification.vm.IdentificationViewModel
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.shared.vm.SharedOrderDetailViewModel

@Composable
fun IdentificationFeatureScreen(
    actions: IdentificationActions,
    orderKey: OrderKey,
    faceSdkLauncher: IdentificationFaceSdkLauncher,
) {
    val sharedOrderDetailViewModel: SharedOrderDetailViewModel = hiltViewModel()
    val identificationViewModel: IdentificationViewModel = hiltViewModel()
    IdentificationRouteScreen(
        actions = actions,
        orderKey = orderKey,
        faceSdkLauncher = faceSdkLauncher,
        sharedOrderDetailViewModel = sharedOrderDetailViewModel,
        identificationViewModel = identificationViewModel,
    )
}
