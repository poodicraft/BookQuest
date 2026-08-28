package com.poodicraft.bookquest.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.poodicraft.bookquest.R
import com.poodicraft.bookquest.data.CloudSync
import com.poodicraft.bookquest.ui.theme.Brand
import kotlinx.coroutines.launch

/** Walks up the context chain to the hosting Activity, which sign in needs. */
internal fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private enum class AuthMode { CHOOSER, EMAIL_SIGN_IN, EMAIL_SIGN_UP }

/**
 * The welcome and sign in screen. Shown on launch while signed out, and from the
 * settings account card. [onSkip] is what "Not now" does; passing null hides it,
 * which is how the settings entry point uses it.
 */
@Composable
fun AuthScreen(
    onSkip: (() -> Unit)?,
    onSignedIn: () -> Unit
) {
    val context = LocalContext.current
    val cloud = remember { CloudSync.get(context) }
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(AuthMode.CHOOSER) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    var noticeRes by remember { mutableStateOf<Int?>(null) }

    fun attempt(block: suspend () -> Result<Unit>) {
        if (busy) return
        busy = true
        errorRes = null
        noticeRes = null
        scope.launch {
            val result = block()
            busy = false
            if (result.isSuccess) {
                onSignedIn()
            } else {
                errorRes = authErrorMessage(result.exceptionOrNull())
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 26.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(Brand.VioletDeep, Brand.Violet, Brand.Bubblegum))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.height(22.dp))
            Text(
                text = stringResource(
                    when (mode) {
                        AuthMode.CHOOSER -> R.string.welcome_title
                        AuthMode.EMAIL_SIGN_IN -> R.string.email_sign_in_title
                        AuthMode.EMAIL_SIGN_UP -> R.string.email_sign_up_title
                    }
                ),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.welcome_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(30.dp))

            if (mode == AuthMode.CHOOSER) {
                BrandButton(
                    iconRes = R.drawable.ic_google,
                    label = stringResource(R.string.continue_google),
                    container = Color.White,
                    content = Color(0xFF1F1F1F),
                    borderColor = Color(0xFFDADCE0),
                    busy = busy,
                    onClick = {
                        val activity = context.findActivity()
                        if (activity != null) attempt { cloud.signIn(activity) }
                    }
                )
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.or_divider),
                        modifier = Modifier.padding(horizontal = 14.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(18.dp))
                BrandButton(
                    icon = {
                        Icon(
                            Icons.Rounded.MailOutline,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    label = stringResource(R.string.continue_email),
                    container = MaterialTheme.colorScheme.surface,
                    content = MaterialTheme.colorScheme.onSurface,
                    borderColor = MaterialTheme.colorScheme.outline,
                    busy = false,
                    onClick = {
                        errorRes = null
                        mode = AuthMode.EMAIL_SIGN_IN
                    }
                )
            } else {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.email_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff
                                else Icons.Rounded.Visibility,
                                contentDescription = stringResource(
                                    if (passwordVisible) R.string.hide_password
                                    else R.string.show_password
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(18.dp))
                BrandButton(
                    icon = null,
                    label = stringResource(
                        if (mode == AuthMode.EMAIL_SIGN_UP) R.string.create_account_action
                        else R.string.sign_in_action
                    ),
                    container = MaterialTheme.colorScheme.primary,
                    content = Color.White,
                    borderColor = null,
                    busy = busy,
                    onClick = {
                        val address = email.trim()
                        when {
                            !address.contains("@") || !address.contains(".") ->
                                errorRes = R.string.error_email_invalid

                            password.length < 6 ->
                                errorRes = R.string.error_password_short

                            mode == AuthMode.EMAIL_SIGN_UP ->
                                attempt { cloud.createAccountWithEmail(address, password) }

                            else ->
                                attempt { cloud.signInWithEmail(address, password) }
                        }
                    }
                )

                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = {
                        errorRes = null
                        noticeRes = null
                        mode = if (mode == AuthMode.EMAIL_SIGN_UP) AuthMode.EMAIL_SIGN_IN
                        else AuthMode.EMAIL_SIGN_UP
                    }
                ) {
                    Text(
                        stringResource(
                            if (mode == AuthMode.EMAIL_SIGN_UP) R.string.have_account
                            else R.string.need_account
                        )
                    )
                }

                if (mode == AuthMode.EMAIL_SIGN_IN) {
                    TextButton(
                        onClick = {
                            val address = email.trim()
                            if (!address.contains("@")) {
                                errorRes = R.string.error_email_invalid
                            } else {
                                busy = true
                                errorRes = null
                                scope.launch {
                                    val result = cloud.sendPasswordReset(address)
                                    busy = false
                                    if (result.isSuccess) {
                                        noticeRes = R.string.reset_sent
                                    } else {
                                        errorRes = authErrorMessage(result.exceptionOrNull())
                                    }
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.forgot_password))
                    }
                }

                TextButton(
                    onClick = {
                        errorRes = null
                        noticeRes = null
                        mode = AuthMode.CHOOSER
                    }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.back))
                    }
                }
            }

            val error = errorRes
            if (error != null) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            val notice = noticeRes
            if (notice != null) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(notice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Brand.Mint,
                    textAlign = TextAlign.Center
                )
            }

            if (busy) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.signing_in),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (onSkip != null) {
                Spacer(Modifier.height(20.dp))
                TextButton(onClick = onSkip, enabled = !busy) {
                    Text(
                        text = stringResource(R.string.not_now),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * One provider button. The label stays optically centred while the mark sits at
 * the leading edge, which is how the Google button is specified.
 */
@Composable
private fun BrandButton(
    label: String,
    container: Color,
    content: Color,
    borderColor: Color?,
    busy: Boolean,
    onClick: () -> Unit,
    iconRes: Int? = null,
    icon: (@Composable () -> Unit)? = null
) {
    Button(
        onClick = onClick,
        enabled = !busy,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container,
            disabledContentColor = content
        ),
        border = if (borderColor != null) BorderStroke(1.dp, borderColor) else null,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = content,
                    strokeWidth = 2.dp
                )
            } else {
                if (iconRes != null) {
                    Image(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 18.dp)
                            .size(20.dp)
                    )
                } else if (icon != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 18.dp)
                    ) {
                        icon()
                    }
                }
                Text(
                    text = label,
                    color = content,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 52.dp)
                )
            }
        }
    }
}

/** Turns a Firebase failure into something a student can actually act on. */
internal fun authErrorMessage(error: Throwable?): Int {
    if (error == null) return R.string.error_generic
    if (error is CloudSync.CloudNotConfigured) return R.string.cloud_not_configured
    if (error is CloudSync.SignInCancelled) return R.string.error_cancelled
    if (error is FirebaseNetworkException) return R.string.error_network
    if (error is FirebaseAuthException) {
        return when (error.errorCode) {
            "ERROR_INVALID_EMAIL" -> R.string.error_email_invalid
            "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> R.string.error_credentials
            "ERROR_USER_NOT_FOUND", "ERROR_USER_DISABLED" -> R.string.error_no_account
            "ERROR_EMAIL_ALREADY_IN_USE",
            "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> R.string.error_email_in_use
            "ERROR_WEAK_PASSWORD" -> R.string.error_password_short
            "ERROR_OPERATION_NOT_ALLOWED" -> R.string.error_provider_off
            "ERROR_TOO_MANY_REQUESTS" -> R.string.error_too_many
            else -> R.string.error_generic
        }
    }
    val message = error.message.orEmpty()
    if (message.contains("not enabled", ignoreCase = true) ||
        message.contains("configuration is not found", ignoreCase = true) ||
        message.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true)
    ) {
        return R.string.error_provider_off
    }
    if (message.contains("canceled", ignoreCase = true) ||
        message.contains("cancelled", ignoreCase = true)
    ) {
        return R.string.error_cancelled
    }
    return R.string.error_generic
}
