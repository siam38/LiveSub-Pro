package com.charles.livecaptionn.ui.l10n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle

val LocalUiStrings = staticCompositionLocalOf<UiStrings> { UiStrings.EMPTY }

@Composable
fun UiStringsProvider(container: com.charles.livecaptionn.di.AppContainer, content: @Composable () -> Unit) {
    val l10nState = container.uiLocalization.state.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalUiStrings provides l10nState.value.strings) {
        content()
    }
}

@Composable
fun rememberUiStrings(): UiStrings = LocalUiStrings.current
