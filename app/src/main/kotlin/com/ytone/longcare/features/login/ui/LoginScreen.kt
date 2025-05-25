package com.ytone.longcare.features.login.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ytone.longcare.R
import com.ytone.longcare.features.login.viewmodel.LoginViewModel
import com.ytone.longcare.theme.LongCareTheme

@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel(), onLoginSuccess: () -> Unit
) {
    var phoneNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val loginState by viewModel.loginState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(loginState) {
        if (loginState is LoginViewModel.LoginUiState.Success) {
            onLoginSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp) // Add some padding around the whole screen
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()) // Make content scrollable if it overflows
                .padding(horizontal = 16.dp), // Horizontal padding for content within the Box
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp)) // Space from top

            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground), // Replace with your app logo
                contentDescription = "App Logo", modifier = Modifier.size(80.dp), // Adjusted size
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "欢迎登录", style = TextStyle(
                    fontSize = 26.sp, // Slightly adjusted size
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface // Use onSurface for better theme adaptability
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "颐康通", // Subtitle or App Name
                style = TextStyle(
                    fontSize = 16.sp, // Slightly adjusted size
                    color = MaterialTheme.colorScheme.onSurfaceVariant // Use onSurfaceVariant
                )
            )

            Spacer(modifier = Modifier.height(40.dp)) // Adjusted spacing

            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("请输入手机号") }, // More descriptive label
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Person, contentDescription = "Phone Number Icon"
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.height(20.dp)) // Adjusted spacing

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("请输入密码") }, // More descriptive label
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Lock, contentDescription = "Password Icon"
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    val image =
                        if (passwordVisible) painterResource(id = R.drawable.ic_visibility_on)
                        else painterResource(id = R.drawable.ic_visibility_off)

                    val description = if (passwordVisible) "隐藏密码" else "显示密码"

                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            painter = image,
                            description,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.height(12.dp)) // Adjusted spacing

            TextButton(
                onClick = { /* TODO: Implement forgot password */ },
                modifier = Modifier.align(Alignment.End) // Align to the end (right)
            ) {
                Text("忘记密码?", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(28.dp)) // Adjusted spacing

            Button(
                onClick = { viewModel.login(phoneNumber, password) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp), // Standard button height
                enabled = loginState !is LoginViewModel.LoginUiState.Loading,
                shape = MaterialTheme.shapes.medium, // Or RoundedCornerShape(8.dp)
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (loginState is LoginViewModel.LoginUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        "登录",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }

            if (loginState is LoginViewModel.LoginUiState.Error) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    (loginState as LoginViewModel.LoginUiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.weight(1f)) // Pushes the following content to the bottom

            TextButton(onClick = { /* TODO: Implement register navigation */ }) {
                Text(
                    "还没有账号? 立即注册",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(16.dp)) // Space from bottom
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    LongCareTheme {
        LoginScreen(onLoginSuccess = {})
    }
}