package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.TheoCyan
import com.example.ui.theme.TheoDarkBackground
import com.example.ui.theme.TheoSurfaceCard
import com.example.ui.theme.TheoSurfaceBorder
import com.example.ui.theme.TheoTextMuted
import com.example.ui.theme.TheoTextPrimary
import com.example.ui.theme.TheoTextSecondary
import com.example.viewmodel.AccountUiState

@Composable
fun AccountGateScreen(
    state: AccountUiState,
    onGoogleSignIn: () -> Unit,
    onSaveProfile: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var username by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.displayName) {
        if (displayName.isBlank()) displayName = state.displayName
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TheoDarkBackground)
            .imePadding()
            .padding(horizontal = 26.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = TheoSurfaceCard,
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, TheoSurfaceBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "THEO",
                    color = TheoTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 5.sp
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = if (state.isSignedIn) "Set up your account" else "Your Theo account",
                    color = TheoTextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (state.isSignedIn) {
                        "Choose your unique username and display name. Your username can be changed later."
                    } else {
                        "Sign in with Google to create your Theo identity. Account and cross-device features will use this identity."
                    },
                    color = TheoTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(24.dp))

                when {
                    state.isLoading -> {
                        CircularProgressIndicator(color = TheoCyan)
                        Spacer(Modifier.height(12.dp))
                        Text("Checking your Theo account…", color = TheoTextMuted, fontSize = 12.sp)
                    }

                    !state.isSignedIn -> {
                        Button(
                            onClick = onGoogleSignIn,
                            enabled = !state.isBusy,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TheoCyan,
                                contentColor = TheoDarkBackground
                            )
                        ) {
                            if (state.isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.height(18.dp),
                                    color = TheoDarkBackground,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Continue with Google", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    else -> {
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it.take(40) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Display name") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words
                            ),
                            colors = accountFieldColors()
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = username,
                            onValueChange = { input ->
                                username = input.trim().lowercase().filter { char ->
                                    char in 'a'..'z' || char in '0'..'9' || char == '_'
                                }.take(20)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Unique username") },
                            prefix = { Text("@", color = TheoTextMuted) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None
                            ),
                            colors = accountFieldColors()
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "3–20 characters · letters, numbers and underscores",
                            color = TheoTextMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(18.dp))
                        Button(
                            onClick = { onSaveProfile(username, displayName) },
                            enabled = !state.isBusy && username.length in 3..20 && displayName.trim().length in 2..40,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TheoCyan,
                                contentColor = TheoDarkBackground
                            )
                        ) {
                            if (state.isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.height(18.dp),
                                    color = TheoDarkBackground,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Create Theo profile", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                state.error?.let { message ->
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun accountFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TheoTextPrimary,
    unfocusedTextColor = TheoTextPrimary,
    focusedBorderColor = TheoCyan,
    unfocusedBorderColor = TheoSurfaceBorder,
    focusedLabelColor = TheoCyan,
    unfocusedLabelColor = TheoTextMuted,
    cursorColor = TheoCyan
)
