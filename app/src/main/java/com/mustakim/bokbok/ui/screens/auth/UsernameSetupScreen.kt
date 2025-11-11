package com.mustakim.bokbok.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.mustakim.bokbok.viewmodel.AuthViewModel
import com.mustakim.bokbok.viewmodel.UserViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.os.Build
import androidx.core.content.ContextCompat
import com.mustakim.bokbok.data.model.PermissionsList
import androidx.compose.ui.platform.LocalContext


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsernameSetupScreen(
    navController: NavHostController,
    authViewModel: AuthViewModel = viewModel(),
    userViewModel: UserViewModel
) {
    val context = LocalContext.current
    val uiState by authViewModel.uiState.collectAsState()

    var username by remember { mutableStateOf("") }
    var isChecking by remember { mutableStateOf(false) }
    var isAvailable by remember { mutableStateOf<Boolean?>(null) }
    var checkJob by remember { mutableStateOf<Job?>(null) }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Check username availability with debounce
    LaunchedEffect(username) {
        if (username.length >= 3) {
            checkJob?.cancel()
            isChecking = true
            checkJob = scope.launch {
                delay(500) // Debounce
                authViewModel.checkUsernameAvailability(username) { available ->
                    isAvailable = available
                    isChecking = false
                }
            }
        } else {
            isAvailable = null
            isChecking = false
        }
    }

    // Navigate after username is set
    LaunchedEffect(uiState.isNewGoogleUser) {
        if (!uiState.isNewGoogleUser && uiState.isLoggedIn) {
            userViewModel.loadCurrentUser()

            // Check permissions
            val requiredPermissions = PermissionsList.getRequiredPermissions()
            val allRequiredGranted = requiredPermissions.all { permission ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    permission.permission == android.Manifest.permission.POST_NOTIFICATIONS) {
                    ContextCompat.checkSelfPermission(
                        context,
                        permission.permission
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                } else {
                    ContextCompat.checkSelfPermission(
                        context,
                        permission.permission
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                }
            }

            if (allRequiredGranted) {
                navController.navigate("lounge") {
                    popUpTo("setup_username") { inclusive = true }
                }
            } else {
                navController.navigate("permissions") {
                    popUpTo("setup_username") { inclusive = true }
                }
            }
        }
    }


    // Show errors
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            authViewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Pick Your Username",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Choose a unique username for your profile",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(48.dp))

            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it.lowercase().filter { char ->
                        char.isLetterOrDigit() || char == '_'
                    }
                },
                label = { Text("Username") },
                leadingIcon = {
                    Icon(Icons.Default.AlternateEmail, contentDescription = null)
                },
                trailingIcon = {
                    when {
                        isChecking -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        isAvailable == true -> {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Available",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        isAvailable == false -> {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Not available",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                prefix = { Text("@") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    when {
                        username.length < 3 && username.isNotEmpty() -> {
                            Text("Username must be at least 3 characters")
                        }
                        isAvailable == false -> {
                            Text("Username is already taken")
                        }
                        isAvailable == true -> {
                            Text("Username is available!")
                        }
                    }
                },
                isError = isAvailable == false || (username.isNotEmpty() && username.length < 3)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { authViewModel.updateUsername(username) },
                modifier = Modifier.fillMaxWidth(),
                enabled = isAvailable == true && !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Continue")
                }
            }
        }
    }
}
