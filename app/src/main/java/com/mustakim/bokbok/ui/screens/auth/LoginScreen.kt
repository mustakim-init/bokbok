package com.mustakim.bokbok.ui.screens.auth

import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.mustakim.bokbok.R
import com.mustakim.bokbok.data.model.PermissionsList
import com.mustakim.bokbok.viewmodel.AuthEvent
import com.mustakim.bokbok.viewmodel.AuthViewModel
import com.mustakim.bokbok.viewmodel.UserViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    navController: NavHostController,
    authViewModel: AuthViewModel = viewModel(),
    userViewModel: UserViewModel
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val uiState by authViewModel.uiState.collectAsState()
    val authEvent by authViewModel.authEvents.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Legacy Google Sign-In launcher
    val legacyGoogleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        scope.launch {
            authViewModel.handleLegacyGoogleSignIn(result.data)
        }
    }

    // Handle one-time auth events
    LaunchedEffect(authEvent) {
        when (authEvent) {
            is AuthEvent.NavigateToUsernameSetup -> {
                authViewModel.clearAuthEvent()
                navController.navigate("google_signup") {
                    popUpTo("login") { inclusive = false }
                }
            }
            is AuthEvent.NavigateToPermissions -> {
                authViewModel.clearAuthEvent()
                userViewModel.loadCurrentUser()

                // Check permissions and navigate
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
                        popUpTo("login") { inclusive = true }
                    }
                } else {
                    navController.navigate("permissions") {
                        popUpTo("login") { inclusive = true }
                    }
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
            // Logo
            Text(
                text = "BokBok",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Voice Calling Made Simple",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Google Sign-In Button
            OutlinedButton(
                onClick = {
                    if (authViewModel.supportsModernAuth()) {
                        // Modern: Check if user exists first
                        if (activity != null) {
                            authViewModel.signInWithGoogle(activity)
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar("Error: Activity not found")
                            }
                        }
                    } else {
                        // Legacy: Use existing flow
                        authViewModel.startLegacyGoogleSignIn { intent ->
                            legacyGoogleSignInLauncher.launch(intent)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                enabled = !uiState.isLoading
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_google),
                    contentDescription = "Google Logo",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Continue with Google")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // OR Divider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    text = "OR",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Email Field
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                leadingIcon = {
                    Icon(Icons.Default.Email, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password Field
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null)
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility
                            else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password"
                            else "Show password"
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Login Button
            Button(
                onClick = { authViewModel.signIn(email, password) },
                modifier = Modifier.fillMaxWidth(),
                enabled = email.isNotBlank() && password.isNotBlank() && !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Login")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sign Up Link
            TextButton(
                onClick = { navController.navigate("signup") }
            ) {
                Text("Don't have an account? Sign Up")
            }
        }
    }
}