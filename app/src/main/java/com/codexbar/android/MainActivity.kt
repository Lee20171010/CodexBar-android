package com.codexbar.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.codexbar.android.core.util.BatteryOptimizationHelper
import com.codexbar.android.core.workmanager.WorkManagerInitializer
import com.codexbar.android.feature.dashboard.DashboardScreen
import com.codexbar.android.feature.settings.SettingsScreen
import com.codexbar.android.ui.theme.CodexBarTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val batteryOptLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // After returning from the battery optimization dialog,
        // schedule workers regardless of the result
        WorkManagerInitializer.scheduleTokenRefresh(this)
        WorkManagerInitializer.schedulePeriodicRefresh(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CodexBarTheme {
                val navController = rememberNavController()
                val snackbarHostState = remember { SnackbarHostState() }

                // Battery optimization exemption
                var showBatteryDialog by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this@MainActivity)) {
                        showBatteryDialog = true
                    }
                }

                if (showBatteryDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            showBatteryDialog = false
                        },
                        title = { Text("Background Token Refresh") },
                        text = {
                            Text(
                                "To keep your API tokens up to date in the background, " +
                                    "please exempt this app from battery optimization."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showBatteryDialog = false
                                batteryOptLauncher.launch(
                                    BatteryOptimizationHelper
                                        .requestIgnoreBatteryOptimizationsIntent(this@MainActivity)
                                )
                            }) {
                                Text("Allow")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                showBatteryDialog = false
                                // Fallback: open system battery settings
                                try {
                                    batteryOptLauncher.launch(
                                        BatteryOptimizationHelper
                                            .openBatteryOptimizationSettingsIntent()
                                    )
                                } catch (_: Exception) {
                                    // Ignore if settings page is not available
                                }
                            }) {
                                Text("Settings")
                            }
                        }
                    )
                }

                // Each screen owns its system-bar insets (TopAppBar + its own Scaffold); a
                // Scaffold here would pad the NavHost with the status-bar inset a second time.
                // The snackbar therefore overlays directly with its own nav-bar/IME safety.
                Box(Modifier.fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = "dashboard"
                    ) {
                        composable("dashboard") {
                            DashboardScreen(
                                onNavigateToSettings = {
                                    navController.navigate("settings")
                                }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                    SnackbarHost(
                        snackbarHostState,
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                    )
                }
            }
        }
    }
}
