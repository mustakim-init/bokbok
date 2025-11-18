package com.mustakim.bokbok.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.mustakim.bokbok.viewmodel.AuthEvent
import com.mustakim.bokbok.viewmodel.AuthViewModel
import com.mustakim.bokbok.viewmodel.UserViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleSignupScreen(
    navController: NavHostController,
    authViewModel: AuthViewModel = viewModel(),
    userViewModel: UserViewModel
) {

    val uiState by authViewModel.uiState.collectAsState()
    val authEvent by authViewModel.authEvents.collectAsState()

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

    // Handle one-time auth events
    LaunchedEffect(authEvent) {
        when (authEvent) {
            is AuthEvent.NavigateToPermissions -> {
                authViewModel.clearAuthEvent()
                userViewModel.loadCurrentUser()
                navController.navigate("permissions") {
                    popUpTo("google_signup") { inclusive = true }
                }
            }
            is AuthEvent.ShowError -> {
                authViewModel.clearAuthEvent()
                val error = (authEvent as AuthEvent.ShowError).message
                snackbarHostState.showSnackbar(error)
            }
            else -> {}
        }
    }

    // Show errors from uiState (for non-navigation errors)
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            authViewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Complete Your Profile") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
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
                text = "Choose Your Username",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Pick a unique username to complete your BokBok profile",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Username Input
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

            // Complete Account Setup Button
            Button(
                onClick = { authViewModel.completeGoogleSignUp(username) },
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
                    Text("Complete Account Setup")
                }
            }
        }
    }
}