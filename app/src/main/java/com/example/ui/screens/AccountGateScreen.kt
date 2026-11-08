package com.example.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.TheoCyan
import com.example.ui.theme.TheoDarkBackground
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
            .background(
                Brush.verticalGradient(
                    listOf(
                        androidx.compose.ui.graphics.Color(0xFF111B2A),
                        TheoDarkBackground,
                        androidx.compose.ui.graphics.Color(0xFF080B12)
                    )
                )
            )
            .imePadding()
            .padding(horizontal = 28.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp)),
                color = TheoCyan.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, TheoCyan.copy(alpha = 0.28f)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(
                    text = "T",
                    modifier = Modifier.padding(horizontal = 17.dp, vertical = 10.dp),
                    color = TheoCyan,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(Modifier.height(34.dp))
            Text(
                text = if (state.isSignedIn) "Make Theo yours." else "Your mind.\nYour Theo.",
                color = TheoTextPrimary,
                fontSize = 39.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = if (state.isSignedIn) {
                    "Choose the identity you'll use across your devices."
                } else {
                    "One account keeps your conversations, memory and workspace connected across devices."
                },
                color = TheoTextSecondary,
                fontSize = 15.sp,
                lineHeight = 23.sp
            )
            Spacer(Modifier.height(34.dp))

            when {
                state.isLoading -> {
                    RowStatus(
                        label = "Connecting to your Theo account",
                        loading = true
                    )
                }

                !state.isSignedIn -> {
                    Button(
                        onClick = onGoogleSignIn,
                        enabled = !state.isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = androidx.compose.ui.graphics.Color(0xFFF5F6F8),
                            contentColor = androidx.compose.ui.graphics.Color(0xFF151922),
                            disabledContainerColor = androidx.compose.ui.graphics.Color(0xFFD9DCE2),
                            disabledContentColor = androidx.compose.ui.graphics.Color(0xFF454B55)
                        )
                    ) {
                        if (state.isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(19.dp),
                                color = androidx.compose.ui.graphics.Color(0xFF151922),
                                strokeWidth = 2.dp
                            )
                            Text(
                                "  Waiting for Google…",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text(
                                "G",
                                color = androidx.compose.ui.graphics.Color(0xFF4285F4),
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                "  Continue with Google",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
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
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = { onSaveProfile(username, displayName) },
                        enabled = !state.isBusy && username.length in 3..20 && displayName.trim().length in 2..40,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TheoCyan)
                    ) {
                        if (state.isBusy) CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = TheoDarkBackground,
                            strokeWidth = 2.dp
                        ) else Text("Create Theo identity", fontWeight = FontWeight.Bold)
                    }
                }
            }

            state.error?.let { message ->
                Spacer(Modifier.height(18.dp))
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.error.copy(alpha = 0.28f)
                    )
                ) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            Spacer(Modifier.height(36.dp))
            Text(
                text = "THEO  /  PERSONAL INTELLIGENCE",
                color = TheoTextMuted,
                fontSize = 10.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun RowStatus(label: String, loading: Boolean) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 18.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (loading) CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = TheoCyan,
            strokeWidth = 2.dp
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 12.dp),
            color = TheoTextSecondary,
            fontSize = 13.sp
        )
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
