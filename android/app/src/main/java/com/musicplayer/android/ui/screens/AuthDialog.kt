package com.musicplayer.android.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.musicplayer.android.R
import com.musicplayer.android.core.viewmodel.MainPlayerViewModel
import com.musicplayer.android.ui.theme.DarkRefTheme

@Composable
fun AuthDialog(
    viewModel: MainPlayerViewModel,
    currentUser: String?,
    authStatus: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(DarkRefTheme.SurfaceCard)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = currentUser?.take(1)?.uppercase() ?: "👤",
                    fontSize = 24.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.account_title),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = DarkRefTheme.TextPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (currentUser != null) {
                Text(
                    text = stringResource(R.string.account_logged_in_as),
                    fontSize = 13.sp,
                    color = DarkRefTheme.TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = currentUser,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        viewModel.logout()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkRefTheme.AccentPink),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.account_logout_btn))
                }
            } else {
                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    label = { Text(stringResource(R.string.account_email_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DarkRefTheme.TextPrimary,
                        unfocusedTextColor = DarkRefTheme.TextPrimary,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text(stringResource(R.string.account_password_hint)) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DarkRefTheme.TextPrimary,
                        unfocusedTextColor = DarkRefTheme.TextPrimary,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                val emptyMsg = stringResource(R.string.account_credentials_empty)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (emailInput.isNotBlank() && passwordInput.isNotBlank()) {
                                viewModel.login(emailInput.trim(), passwordInput)
                            } else {
                                Toast.makeText(context, emptyMsg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                    ) {
                        Text(stringResource(R.string.account_login_btn), color = DarkRefTheme.BackgroundDark, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (emailInput.isNotBlank() && passwordInput.isNotBlank()) {
                                val uname = emailInput.substringBefore("@")
                                viewModel.register(uname, emailInput.trim(), passwordInput)
                            } else {
                                Toast.makeText(context, emptyMsg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text(stringResource(R.string.account_register_btn), color = DarkRefTheme.TextPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        emailInput = "ivan@example.com"
                        passwordInput = "Test12345"
                    }
                ) {
                    Text(
                        text = stringResource(R.string.account_demo_btn),
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            if (!authStatus.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = authStatus,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_close), color = DarkRefTheme.TextSecondary)
            }
        }
    }
}
