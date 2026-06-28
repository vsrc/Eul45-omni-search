package com.omnisearch.app.ui

import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.omnisearch.app.ui.theme.FluentTheme

@Composable
fun AppLockScreen(
    securityViewModel: SecurityViewModel,
    modifier: Modifier = Modifier
) {
    val isLocked by securityViewModel.isLocked.collectAsState()
    val enteredPin by securityViewModel.enteredPin.collectAsState()
    val isBiometricEnabled by securityViewModel.isBiometricEnabled.collectAsState()
    val validationState by securityViewModel.pinValidationState.collectAsState()

    val context = LocalContext.current
    val activity = context as? FragmentActivity

    // Automatic biometric trigger on start
    if (isLocked && isBiometricEnabled && activity != null) {
        LaunchedEffect(Unit) {
            triggerBiometrics(
                activity = activity,
                onSuccess = { securityViewModel.bypassWithBiometrics() },
                onError = { /* fallback to PIN */ }
            )
        }
    }

    AnimatedVisibility(
        visible = isLocked,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(FluentTheme.colors.pageBg),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(FluentTheme.dims.paddingExtraLarge)
            ) {
                // Header lock icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(FluentTheme.colors.accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "App Locked",
                        tint = FluentTheme.colors.accent,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "OmniSearch Protected",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = FluentTheme.colors.textColor
                )

                Spacer(modifier = Modifier.height(8.dp))

                val subText = when (validationState) {
                    is PinValidationState.Error -> (validationState as PinValidationState.Error).message
                    else -> "Enter your 4-digit security PIN"
                }
                val subColor = when (validationState) {
                    is PinValidationState.Error -> FluentTheme.colors.dangerText
                    else -> FluentTheme.colors.textMuted
                }

                Text(
                    text = subText,
                    fontSize = 14.sp,
                    color = subColor,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Entered digits indicator row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0..3) {
                        val isFilled = enteredPin.length > i
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .border(2.dp, FluentTheme.colors.accent, CircleShape)
                                .background(
                                    if (isFilled) FluentTheme.colors.accent else androidx.compose.ui.graphics.Color.Transparent
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("bio", "0", "del")
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    rows.forEach { rowKeys ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rowKeys.forEach { key ->
                                when (key) {
                                    "bio" -> {
                                        if (isBiometricEnabled && activity != null) {
                                            Box(
                                                modifier = Modifier
                                                    .size(64.dp)
                                                    .clip(CircleShape)
                                                    .background(FluentTheme.colors.surfaceBg)
                                                    .border(1.dp, FluentTheme.colors.panelBorder, CircleShape)
                                                    .clickable {
                                                        triggerBiometrics(
                                                            activity = activity,
                                                            onSuccess = { securityViewModel.bypassWithBiometrics() },
                                                            onError = {}
                                                        )
                                                    }
                                                    .testTag("biometric_login_button"),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Fingerprint,
                                                    contentDescription = "Verify Biometrics",
                                                    tint = FluentTheme.colors.accent,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.size(64.dp))
                                        }
                                    }
                                    "del" -> {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape)
                                                .background(FluentTheme.colors.surfaceBg)
                                                .border(1.dp, FluentTheme.colors.panelBorder, CircleShape)
                                                .clickable { securityViewModel.removeLastPinDigit() }
                                                .testTag("delete_digit_button"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Backspace,
                                                contentDescription = "Backspace",
                                                tint = FluentTheme.colors.textColor,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                    else -> {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape)
                                                .background(FluentTheme.colors.surfaceBg)
                                                .border(1.dp, FluentTheme.colors.panelBorder, CircleShape)
                                                .clickable { securityViewModel.enterPinDigit(key) }
                                                .testTag("keypad_digit_$key"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = key,
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.Normal,
                                                color = FluentTheme.colors.textColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun triggerBiometrics(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val biometricPrompt = BiometricPrompt(activity, executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onError("Verification failed")
            }
        })

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock OmniSearch")
        .setSubtitle("Authenticate securely using your device credentials")
        .setNegativeButtonText("Use PIN")
        .build()

    try {
        biometricPrompt.authenticate(promptInfo)
    } catch (e: Exception) {
        onError(e.localizedMessage ?: "Biometric error")
    }
}
