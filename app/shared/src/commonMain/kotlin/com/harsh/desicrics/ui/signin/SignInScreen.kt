package com.harsh.desicrics.ui.signin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harsh.desicrics.auth.SocialProvider
import com.harsh.desicrics.auth.rememberPhoneVerifier
import com.harsh.desicrics.auth.rememberSocialSignIn
import com.harsh.desicrics.ui.components.ChoiceChips
import org.koin.compose.viewmodel.koinViewModel

private enum class OtherMethod(val label: String) {
    PHONE("Mobile number"),
    EMAIL("Email"),
}

@Composable
fun SignInScreen(viewModel: SignInViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val socialSignIn = rememberSocialSignIn()
    var otherMethod by rememberSaveable { mutableStateOf(OtherMethod.PHONE) }
    val busy = state.inProgress != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("🏏", style = MaterialTheme.typography.displayMedium)
        Text("DesiCrics", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(
            "Score every gully match like it's a World Cup final",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        socialSignIn.providers.forEach { provider ->
            SocialButton(
                provider = provider,
                loading = state.inProgress?.name == provider.name,
                enabled = !busy,
                onClick = { viewModel.signIn(provider, socialSignIn) },
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            HorizontalDivider(Modifier.weight(1f))
            Text("  or continue with  ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(Modifier.weight(1f))
        }
        ChoiceChips(
            options = OtherMethod.entries,
            selected = otherMethod,
            label = { it.label },
            onSelect = {
                otherMethod = it
                viewModel.clearFeedback()
            },
        )

        when (otherMethod) {
            OtherMethod.PHONE -> PhoneSignIn(state, viewModel)
            OtherMethod.EMAIL -> EmailSignIn(state, viewModel)
        }

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center) }
    }
}

@Composable
private fun SocialButton(provider: SocialProvider, loading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val content: @Composable () -> Unit = {
        if (loading) ButtonProgress() else Text("Continue with ${provider.displayName}")
    }
    // Apple's guidelines ask for a black button; Google's for a neutral outlined one.
    if (provider == SocialProvider.APPLE) {
        Button(
            onClick = onClick,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { content() }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(52.dp)) { content() }
    }
}

@Composable
private fun PhoneSignIn(state: SignInUiState, viewModel: SignInViewModel) {
    val verifier = rememberPhoneVerifier()
    val loading = state.inProgress == SignInMethod.PHONE
    val enabled = state.inProgress == null

    when (val step = state.phoneStep) {
        PhoneStep.EnterNumber -> {
            var phone by rememberSaveable { mutableStateOf("+91 ") }
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Mobile number") },
                placeholder = { Text("+91 98765 43210") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton("Send OTP", loading, enabled) { viewModel.sendPhoneCode(phone, verifier) }
        }
        is PhoneStep.EnterCode -> {
            var code by rememberSaveable(step.verificationId) { mutableStateOf("") }
            OutlinedTextField(
                value = code,
                onValueChange = { value -> code = value.filter { it.isDigit() }.take(6) },
                label = { Text("6-digit code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton("Verify", loading, enabled) { viewModel.verifyPhoneCode(code) }
            Row {
                TextButton(onClick = { viewModel.sendPhoneCode(step.phoneNumber, verifier) }, enabled = enabled) { Text("Resend code") }
                TextButton(onClick = viewModel::changePhoneNumber, enabled = enabled) { Text("Change number") }
            }
        }
    }
}

@Composable
private fun EmailSignIn(state: SignInUiState, viewModel: SignInViewModel) {
    var creatingAccount by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    val loading = state.inProgress == SignInMethod.EMAIL
    val enabled = state.inProgress == null

    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text("Email") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Password") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    if (creatingAccount) {
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirm password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        PrimaryButton("Create account", loading, enabled) { viewModel.createAccountWithEmail(email, password, confirmPassword) }
    } else {
        PrimaryButton("Sign in", loading, enabled) { viewModel.signInWithEmail(email, password) }
        TextButton(onClick = { viewModel.sendPasswordReset(email) }, enabled = enabled) { Text("Forgot password?") }
    }
    TextButton(
        onClick = {
            creatingAccount = !creatingAccount
            viewModel.clearFeedback()
        },
        enabled = enabled,
    ) {
        Text(if (creatingAccount) "Already have an account? Sign in" else "New here? Create an account")
    }
}

@Composable
private fun PrimaryButton(text: String, loading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        if (loading) ButtonProgress() else Text(text)
    }
}

@Composable
private fun ButtonProgress() = CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
