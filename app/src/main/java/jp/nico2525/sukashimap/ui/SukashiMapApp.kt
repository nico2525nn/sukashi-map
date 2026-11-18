package jp.nico2525.sukashimap.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
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
    val context = LocalContext.current
    var pendingSharedUri by rememberSaveable { mutableStateOf<String?>(null) }

    fun openSharedUri(uri: Uri) {
        viewModel.openMediaUri(uri) {
            route = ScreenRoute.EDITOR
        }
    }

    val sharedLocationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            viewModel.emitMessage("写真の位置情報が許可されていないため、撮影地は手動で設定できます")
        }
        val pending = pendingSharedUri
        pendingSharedUri = null
        if (pending != null) {
            openSharedUri(Uri.parse(pending))
            onConsumedSharedUri()
        }
    }
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
        val uri = incomingSharedUri ?: return@LaunchedEffect
        val hasMediaLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_MEDIA_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasMediaLocationPermission) {
            pendingSharedUri = null
            openSharedUri(uri)
            onConsumedSharedUri()
        } else {
            pendingSharedUri = uri.toString()
            sharedLocationPermissionLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
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
