package com.squads.app.ui.auth

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.squads.app.auth.DeviceCodeState

@Composable
fun LoginScreen(
    deviceCodeState: DeviceCodeState,
    onRequestCode: () -> Unit,
    onOpenBrowser: (Activity) -> Unit,
    onMockLogin: () -> Unit,
    onReset: () -> Unit,
) {
    val activity = LocalContext.current as Activity
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Groups,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Squads",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Microsoft Teams & Outlook\non your terms",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(48.dp))

        when (deviceCodeState) {
            is DeviceCodeState.Idle -> {
                Button(
                    onClick = onRequestCode,
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                ) {
                    Text("Sign in with Microsoft", modifier = Modifier.padding(vertical = 8.dp))
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onMockLogin,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Continue with demo data", modifier = Modifier.padding(vertical = 8.dp))
                }
            }

            is DeviceCodeState.CodeReady -> {
                Text(
                    "Your sign-in code:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = deviceCodeState.userCode,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )

                Spacer(Modifier.height(16.dp))

                OutlinedButton(
                    onClick = { clipboardManager.setText(AnnotatedString(deviceCodeState.userCode)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Copy code")
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(deviceCodeState.userCode))
                        onOpenBrowser(activity)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open browser & sign in", modifier = Modifier.padding(vertical = 8.dp))
                }

                Spacer(Modifier.height(16.dp))

                OutlinedButton(onClick = onReset) {
                    Text("Cancel")
                }
            }

            is DeviceCodeState.Polling -> {
                Text(
                    "Your code:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = deviceCodeState.userCode,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )

                Spacer(Modifier.height(24.dp))

                CircularProgressIndicator(modifier = Modifier.size(32.dp))

                Spacer(Modifier.height(8.dp))

                Text(
                    "Paste the code in your browser,\nthen sign in with your account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(16.dp))

                OutlinedButton(onClick = onReset) {
                    Text("Cancel")
                }
            }

            is DeviceCodeState.Success -> {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    "Signed in!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            is DeviceCodeState.Error -> {
                Text(
                    text = deviceCodeState.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = onRequestCode,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Try again", modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Only organization accounts (school/work)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
