package com.harsh.desicrics

import androidx.compose.ui.window.ComposeUIViewController
import com.harsh.desicrics.auth.NativeSignInBridge
import com.harsh.desicrics.di.initKoin
import org.koin.dsl.module

fun MainViewController() = ComposeUIViewController { App() }

/** Called from `iOSApp.init()` after `FirebaseApp.configure()`. */
fun startKoinIos(nativeSignIn: NativeSignInBridge) {
    initKoin {
        modules(module { single<NativeSignInBridge> { nativeSignIn } })
    }
}
