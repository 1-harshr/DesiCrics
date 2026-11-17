package com.harsh.desicrics.ui.signin

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harsh.desicrics.auth.SignInError
import com.harsh.desicrics.auth.SocialProvider
import com.harsh.desicrics.auth.rememberPhoneVerifier
import com.harsh.desicrics.auth.rememberSocialSignIn
import com.harsh.desicrics.designsystem.component.OtpField
import com.harsh.desicrics.designsystem.component.PhoneNumberField
import com.harsh.desicrics.designsystem.component.PrimaryButton
import com.harsh.desicrics.designsystem.component.TonalButton
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.icon.DesiIllustrations
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.resources.Res
import com.harsh.desicrics.resources.app_name
import com.harsh.desicrics.resources.signin_change_number
import com.harsh.desicrics.resources.signin_code_hint
import com.harsh.desicrics.resources.signin_code_sent
import com.harsh.desicrics.resources.signin_code_title
import com.harsh.desicrics.resources.signin_continue_with
import com.harsh.desicrics.resources.signin_hero_subtitle
import com.harsh.desicrics.resources.signin_hero_title
import com.harsh.desicrics.resources.signin_other_ways
import com.harsh.desicrics.resources.signin_phone_country
import com.harsh.desicrics.resources.signin_phone_hint
import com.harsh.desicrics.resources.signin_phone_label
import com.harsh.desicrics.resources.signin_phone_placeholder
import com.harsh.desicrics.resources.signin_phone_prefix
import com.harsh.desicrics.resources.signin_phone_title
import com.harsh.desicrics.resources.signin_resend_code
import com.harsh.desicrics.resources.signin_send_code
import com.harsh.desicrics.resources.signin_verify
import com.harsh.desicrics.ui.system.LightStatusBarIcons
import com.harsh.desicrics.ui.text.label
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Which form the sign-in card shows: the mobile number, then the code sent to it. */
private enum class Mode { PHONE, CODE }

/** How far the card overlaps the green header, so its rounded corners sit on the header. */
private val CardOverlap = 28.dp
private val CardShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
private const val PHONE_DIGITS = 10

@Composable
fun SignInScreen(viewModel: SignInViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val socialSignIn = rememberSocialSignIn()
    val verifier = rememberPhoneVerifier()

    LightStatusBarIcons()
    SignInContent(
        state = state,
        providers = socialSignIn.providers,
        onSendCode = { viewModel.sendPhoneCode(it, verifier) },
        onVerifyCode = viewModel::verifyPhoneCode,
        onChangeNumber = viewModel::changePhoneNumber,
        onSocialSignIn = { viewModel.signIn(it, socialSignIn) },
    )
}

/** The sign-in screen without its ViewModel, so previews and tests can render any state. */
@Composable
fun SignInContent(
    state: SignInUiState,
    providers: List<SocialProvider>,
    onSendCode: (phoneNumber: String) -> Unit,
    onVerifyCode: (code: String) -> Unit,
    onChangeNumber: () -> Unit,
    onSocialSignIn: (SocialProvider) -> Unit,
) {
    val mode = if (state.phoneStep is PhoneStep.EnterCode) Mode.CODE else Mode.PHONE
    val colors = DesiTheme.colors
    val quick = DesiTheme.motion.QUICK_MS
    val base = DesiTheme.motion.BASE_MS

    Box(Modifier.fillMaxSize().background(colors.surface)) {
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
            Hero()
            Column(
                Modifier
                    .offset(y = -CardOverlap)
                    .clip(CardShape)
                    .background(colors.surface)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = DesiTheme.space.s6, vertical = DesiTheme.space.s8),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedContent(
                    targetState = mode,
                    transitionSpec = { fadeIn(tween(base)) togetherWith fadeOut(tween(quick)) },
                    label = "sign-in form",
                    modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                ) { current ->
                    Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s4)) {
                        // Each form shows Feedback(state) right under its main button, where the eye already is.
                        when (current) {
                            Mode.PHONE -> PhoneForm(state, providers, onSendCode, onSocialSignIn)
                            Mode.CODE -> CodeForm(state, onVerifyCode, onChangeNumber, onResend = onSendCode)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Full-bleed green header. The app icon's ball is the hero: drawn large on the right, bleeding off the
 * edge above the crease, and delivered in from the left on first show. Name, headline and pitch below.
 */
@Composable
private fun Hero() {
    val colors = DesiTheme.colors
    val crease = colors.onBrandStrong.copy(alpha = 0.28f)
    val stripe = colors.onBrandStrong.copy(alpha = 0.045f)

    // The delivery: slides in from the left and settles. System "remove animations" makes this instant,
    // and previews show it already settled.
    val inPreview = LocalInspectionMode.current
    val travel = remember { Animatable(if (inPreview) 1f else 0f) }
    LaunchedEffect(Unit) {
        travel.animateTo(1f, tween(durationMillis = 700, easing = DesiTheme.motion.emphasizedDecelerate))
    }

    Box(
        Modifier
            .fillMaxWidth()
            .background(colors.brandStrong)
            .drawBehind {
                // A lighter pitch strip under the ball, as on the app icon.
                val stripWidth = size.width * 0.42f
                drawRect(stripe, topLeft = Offset(size.width - stripWidth, 0f), size = Size(stripWidth, size.height))
            },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = DesiTheme.space.s4, bottom = CardOverlap + DesiTheme.space.s8),
        ) {
            Text(
                stringResource(Res.string.app_name).uppercase(),
                style = DesiTheme.type.label,
                color = colors.onBrandStrong.copy(alpha = 0.85f),
                modifier = Modifier.padding(horizontal = DesiTheme.space.s6),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(HeroArtHeight)
                    .drawBehind {
                        // The popping crease the ball is about to cross.
                        drawRoundRect(
                            crease,
                            topLeft = Offset(size.width * 0.35f, size.height - 6.dp.toPx()),
                            size = Size(size.width * 0.65f, 2.dp.toPx()),
                            cornerRadius = CornerRadius(1.dp.toPx()),
                        )
                    },
            ) {
                Image(
                    painterResource(DesiIllustrations.BallHero),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .requiredWidth(HeroArtWidth)
                        .aspectRatio(54f / 34f)
                        // Bleed the ball off the right edge.
                        .offset(x = HeroArtWidth * 0.18f)
                        .graphicsLayer {
                            translationX = -(1f - travel.value) * size.width * 1.1f
                            alpha = travel.value
                        },
                )
            }
            Column(
                Modifier.padding(horizontal = DesiTheme.space.s6),
                verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s3),
            ) {
                Text(stringResource(Res.string.signin_hero_title), style = DesiTheme.type.scoreXl, color = colors.onBrandStrong)
                Text(
                    stringResource(Res.string.signin_hero_subtitle),
                    style = DesiTheme.type.bodyLg,
                    color = colors.onBrandStrong.copy(alpha = 0.78f),
                )
            }
        }
    }
}

private val HeroArtWidth = 300.dp
private val HeroArtHeight = 190.dp

@Composable
private fun FormTitle(title: StringResource, hint: String? = null) {
    Column(
        verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s1),
        modifier = Modifier.padding(bottom = DesiTheme.space.s2),
    ) {
        Text(stringResource(title), style = DesiTheme.type.titleLg, color = DesiTheme.colors.ink)
        if (hint != null) Text(hint, style = DesiTheme.type.body, color = DesiTheme.colors.muted)
    }
}

@Composable
private fun PhoneForm(
    state: SignInUiState,
    providers: List<SocialProvider>,
    onSendCode: (String) -> Unit,
    onSocialSignIn: (SocialProvider) -> Unit,
) {
    val enabled = state.inProgress == null
    val prefix = stringResource(Res.string.signin_phone_prefix)
    var digits by rememberSaveable { mutableStateOf("") }
    val complete = digits.length == PHONE_DIGITS
    val send = { if (complete) onSendCode("$prefix$digits") }

    FormTitle(Res.string.signin_phone_title, stringResource(Res.string.signin_phone_hint))
    PhoneNumberField(
        value = digits,
        onValueChange = { digits = it },
        label = stringResource(Res.string.signin_phone_label),
        countryCode = prefix,
        countryLabel = stringResource(Res.string.signin_phone_country),
        placeholder = stringResource(Res.string.signin_phone_placeholder),
        length = PHONE_DIGITS,
        enabled = enabled,
        error = state.error?.takeIf { it == SignInError.PHONE_INVALID }?.let { stringResource(it.label) },
        onDone = send,
    )
    PrimaryButton(
        text = stringResource(Res.string.signin_send_code),
        onClick = send,
        loading = state.inProgress == SignInMethod.PHONE,
        enabled = enabled && complete,
        trailingIcon = DesiIcons.Forward,
        modifier = Modifier.fillMaxWidth(),
    )
    Feedback(state)

    OtherWays {
        providers.forEach { provider ->
            TonalButton(
                text = stringResource(Res.string.signin_continue_with, provider.displayName),
                onClick = { onSocialSignIn(provider) },
                leadingIcon = if (provider == SocialProvider.GOOGLE) DesiIllustrations.GoogleLogo else null,
                tintLeadingIcon = false,
                enabled = enabled,
                loading = state.inProgress?.name == provider.name,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Secondary sign-in options under a quiet label, spaced away from the main action. */
@Composable
private fun OtherWays(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = DesiTheme.space.s4),
        verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s3),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(Res.string.signin_other_ways).uppercase(),
            style = DesiTheme.type.label,
            color = DesiTheme.colors.muted,
        )
        content()
    }
}

@Composable
private fun CodeForm(
    state: SignInUiState,
    onVerifyCode: (String) -> Unit,
    onChangeNumber: () -> Unit,
    onResend: (String) -> Unit,
) {
    val step = state.phoneStep as? PhoneStep.EnterCode ?: return
    val enabled = state.inProgress == null
    var code by rememberSaveable(step.verificationId) { mutableStateOf("") }

    FormTitle(Res.string.signin_code_title, stringResource(Res.string.signin_code_hint, step.phoneNumber))
    OtpField(
        value = code,
        onValueChange = {
            code = it
            // Submit as soon as the last digit is typed or pasted.
            if (it.length == 6) onVerifyCode(it)
        },
        enabled = enabled,
        isError = state.error == SignInError.CODE_WRONG || state.error == SignInError.CODE_EXPIRED,
    )
    PrimaryButton(
        text = stringResource(Res.string.signin_verify),
        onClick = { onVerifyCode(code) },
        loading = state.inProgress == SignInMethod.PHONE,
        enabled = enabled && code.length == 6,
        trailingIcon = DesiIcons.Forward,
        modifier = Modifier.fillMaxWidth(),
    )
    Feedback(state)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        LinkButton(Res.string.signin_change_number, enabled, onChangeNumber)
        LinkButton(Res.string.signin_resend_code, enabled) { onResend(step.phoneNumber) }
    }
}

/** Errors that belong to a field are shown on that field; everything else shows here. */
@Composable
private fun Feedback(state: SignInUiState) {
    val colors = DesiTheme.colors
    state.error?.takeIf { it != SignInError.PHONE_INVALID }?.let {
        Text(stringResource(it.label), style = DesiTheme.type.body, color = colors.event.wicket)
    }
    state.message?.let { message ->
        val text = when (message) {
            is SignInMessage.CodeSent -> stringResource(Res.string.signin_code_sent, message.phoneNumber)
        }
        Text(text, style = DesiTheme.type.body, color = colors.brand)
    }
}

@Composable
private fun LinkButton(text: StringResource, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(stringResource(text), style = DesiTheme.type.button, color = if (enabled) DesiTheme.colors.brand else DesiTheme.colors.muted)
    }
}

// Previews: sample phone number only; all labels come from string resources.

private const val PreviewNumber = "+919876543210"

@Composable
private fun SignInPreview(state: SignInUiState, darkTheme: Boolean = false) = DesiTheme(darkTheme = darkTheme) {
    SignInContent(state, listOf(SocialProvider.GOOGLE), {}, {}, {}, {})
}

@Preview
@Composable
private fun SignInPhonePreview() = SignInPreview(SignInUiState())

@Preview
@Composable
private fun SignInPhoneDarkPreview() = SignInPreview(SignInUiState(), darkTheme = true)

@Preview
@Composable
private fun SignInSendingPreview() = SignInPreview(SignInUiState(inProgress = SignInMethod.PHONE))

@Preview
@Composable
private fun SignInGoogleNotSetUpPreview() = SignInPreview(SignInUiState(error = SignInError.METHOD_DISABLED))

@Preview
@Composable
private fun SignInCodePreview() = SignInPreview(
    SignInUiState(
        phoneStep = PhoneStep.EnterCode(PreviewNumber, verificationId = "preview"),
        message = SignInMessage.CodeSent(PreviewNumber),
    ),
)

@Preview
@Composable
private fun SignInWrongCodePreview() = SignInPreview(
    SignInUiState(phoneStep = PhoneStep.EnterCode(PreviewNumber, verificationId = "preview"), error = SignInError.CODE_WRONG),
    darkTheme = true,
)
