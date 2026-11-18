package jp.nico2525.sukashimap.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import jp.nico2525.sukashimap.ui.camera.CameraOverlayScreen
import jp.nico2525.sukashimap.ui.editor.EditorScreen
import jp.nico2525.sukashimap.ui.theme.ThemeMode

private enum class ScreenRoute {
    EDITOR,
    CAMERA
}

@Composable
fun SukashiMapApp(
    viewModel: SukashiViewModel,
    incomingSharedUri: Uri? = null,
    onConsumedSharedUri: () -> Unit = {}
) {
    var route by rememberSaveable { mutableStateOf(ScreenRoute.EDITOR) }
    val snackbarHostState = remember { SnackbarHostState() }
    val prefs by viewModel.prefsState.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val isAppDark = when (prefs.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        val useDarkSurfaceBars = isAppDark || route == ScreenRoute.CAMERA
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !useDarkSurfaceBars
            insetsController.isAppearanceLightNavigationBars = !useDarkSurfaceBars
        }
    }

    LaunchedEffect(incomingSharedUri) {
        if (incomingSharedUri != null) {
            viewModel.openMediaUri(incomingSharedUri) {
                route = ScreenRoute.EDITOR
            }
            onConsumedSharedUri()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    BackHandler(enabled = route == ScreenRoute.CAMERA) {
        route = ScreenRoute.EDITOR
    }

    AnimatedContent(
        targetState = route,
        label = "screenTransition"
    ) { target ->
        when (target) {
            ScreenRoute.EDITOR -> EditorScreen(
                viewModel = viewModel,
                snackbarHostState = snackbarHostState,
                onOpenCamera = { route = ScreenRoute.CAMERA }
            )
            ScreenRoute.CAMERA -> CameraOverlayScreen(
                viewModel = viewModel,
                onCapturedToEditor = { route = ScreenRoute.EDITOR },
                onBack = { route = ScreenRoute.EDITOR }
            )
        }
    }
}
