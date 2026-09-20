package dev.capriguard.labelguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.capriguard.labelguard.core.GuardTheme
import dev.capriguard.labelguard.label.LabelViewModel
import dev.capriguard.labelguard.ui.HomeScreen
import dev.capriguard.labelguard.ui.SettingsScreen

private enum class Route { Home, Settings }

/**
 * No share target, unlike the text apps in this family. A nutrition label is not
 * something another app hands you as text; it is a table you are standing in front
 * of. There is also no screenshot block here, because the reading is the sort of
 * thing you would want to show somebody — and nothing persists to leak anyway.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { GuardTheme { LabelGuardRoot() } }
    }
}

@Composable
fun LabelGuardRoot(vm: LabelViewModel = viewModel()) {
    var route by remember { mutableStateOf(Route.Home) }
    val activity = LocalContext.current as? ComponentActivity

    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) vm.wipe() }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }

    BackHandler(enabled = route == Route.Settings) { route = Route.Home }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                if (targetState == Route.Settings) {
                    (slideInHorizontally(tween(230)) { it / 5 } + fadeIn(tween(140))) togetherWith
                        (slideOutHorizontally(tween(230)) { -it / 6 } + fadeOut(tween(120)))
                } else {
                    (slideInHorizontally(tween(230)) { -it / 6 } + fadeIn(tween(140))) togetherWith
                        (slideOutHorizontally(tween(230)) { it / 5 } + fadeOut(tween(120)))
                }
            },
            label = "route",
        ) { current ->
            when (current) {
                Route.Home -> HomeScreen(vm = vm, onOpenSettings = { route = Route.Settings })
                Route.Settings -> SettingsScreen(vm = vm, onBack = { route = Route.Home })
            }
        }
    }
}
