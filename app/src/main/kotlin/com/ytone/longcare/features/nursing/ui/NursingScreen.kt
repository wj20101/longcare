package com.ytone.longcare.features.nursing.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.ytone.longcare.features.nursing.viewmodel.NursingViewModel
import com.ytone.longcare.theme.LongCareTheme

@Composable
fun NursingScreen(
    navController: NavController, // Added NavController for navigation
    viewModel: NursingViewModel = hiltViewModel()
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "护理工作页面")
    }
}

@Preview(showBackground = true)
@Composable
fun NursingScreenPreview() {
    LongCareTheme {
        NursingScreen(navController = rememberNavController())
    }
}