package com.mustakim.bokbok.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.mustakim.bokbok.viewmodel.AuthEvent
import com.mustakim.bokbok.viewmodel.AuthViewModel
import com.mustakim.bokbok.viewmodel.UserViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.mustakim.bokbok.ui.shared.BokBokIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleSignupScreen(
    navController: NavHostController,
    authViewModel: AuthViewModel = hiltViewModel(),
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
                title = { Text("Complete Your Profile", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    BokBokIconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Ambient glow
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .align(Alignment.BottomStart)
                    .offset(x = (-50).dp, y = 50.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Choose Your Username",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Pick a unique username to complete your BokBok profile",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(48.dp))

                // Username Input - Puffy style
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
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    ),
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
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    enabled = isAvailable == true && !uiState.isLoading
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Complete Account Setup", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}