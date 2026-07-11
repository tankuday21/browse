package com.udaytank.browse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.udaytank.browse.ui.BookmarksScreen
import com.udaytank.browse.ui.BrowserScreen
import com.udaytank.browse.ui.HistoryScreen
import com.udaytank.browse.ui.theme.BrowseTheme

class MainActivity : ComponentActivity() {

    private val viewModel: BrowserViewModel by viewModels { BrowserViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BrowseTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "browser") {
                    composable("browser") {
                        BrowserScreen(
                            viewModel = viewModel,
                            onOpenHistory = { navController.navigate("history") },
                            onOpenBookmarks = { navController.navigate("bookmarks") },
                        )
                    }
                    composable("history") {
                        HistoryScreen(
                            viewModel = viewModel,
                            onOpenUrl = { url ->
                                viewModel.onOpenUrl(url)
                                navController.popBackStack()
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("bookmarks") {
                        BookmarksScreen(
                            viewModel = viewModel,
                            onOpenUrl = { url ->
                                viewModel.onOpenUrl(url)
                                navController.popBackStack()
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
