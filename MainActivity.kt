package com.fastget.downloader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fastget.downloader.ui.screens.AddDownloadBottomSheet
import com.fastget.downloader.ui.screens.DownloadDetailScreen
import com.fastget.downloader.ui.screens.HomeScreen
import com.fastget.downloader.ui.screens.SettingsScreen
import com.fastget.downloader.ui.theme.FastGetTheme
import com.fastget.downloader.ui.viewmodel.DownloadViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: DownloadViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle shared URL from external browser
        val sharedUrl = extractSharedUrl(intent)

        setContent {
            FastGetTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    var showAddSheet by remember { mutableStateOf(sharedUrl != null) }

                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                viewModel = viewModel,
                                onOpenAddDownload = { showAddSheet = true },
                                onOpenSettings = { navController.navigate("settings") },
                                onOpenTaskDetail = { taskId -> navController.navigate("detail/$taskId") }
                            )

                            if (showAddSheet) {
                                AddDownloadBottomSheet(
                                    viewModel = viewModel,
                                    initialUrl = sharedUrl,
                                    onDismiss = { showAddSheet = false }
                                )
                            }
                        }

                        composable("detail/{taskId}") { backStackEntry ->
                            val taskId = backStackEntry.arguments?.getString("taskId") ?: ""
                            DownloadDetailScreen(
                                taskId = taskId,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("settings") {
                            SettingsScreen(onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let {
            val sharedUrl = extractSharedUrl(it)
            if (sharedUrl != null) {
                viewModel.inspectUrl(sharedUrl)
            }
        }
    }

    private fun extractSharedUrl(intent: Intent?): String? {
        if (intent == null) return null
        if (Intent.ACTION_SEND == intent.action && "text/plain" == intent.type) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank() && (text.startsWith("http://") || text.startsWith("https://"))) {
                return text.trim()
            }
        } else if (Intent.ACTION_VIEW == intent.action && intent.data != null) {
            return intent.data.toString()
        }
        return null
    }
}
