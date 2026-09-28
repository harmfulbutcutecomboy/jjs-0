package com.jjs.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jjs.studio.ui.components.FloatingCapsuleHeader
import com.jjs.studio.ui.dialogs.AddNodeModalSheet
import com.jjs.studio.ui.dialogs.ExportModalSheet
import com.jjs.studio.ui.dialogs.ImportModalSheet
import com.jjs.studio.ui.screens.ConvertScreen
import com.jjs.studio.ui.screens.EditorScreen
import com.jjs.studio.ui.screens.ExplorerScreen
import com.jjs.studio.ui.screens.HomeScreen
import com.jjs.studio.ui.theme.BlackBackground
import com.jjs.studio.ui.theme.JjsStudioTheme
import com.jjs.studio.viewmodel.AppScreen
import com.jjs.studio.viewmodel.JjsViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: JjsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JjsStudioTheme {
                val uiState by viewModel.uiState.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                // Show status toasts
                LaunchedEffect(uiState.statusMessage) {
                    uiState.statusMessage?.let { msg ->
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = msg,
                                duration = SnackbarDuration.Short
                            )
                            viewModel.clearStatus()
                        }
                    }
                }

                // Android Back Handling: Pop back to Home if in sub-screen
                BackHandler(enabled = uiState.currentScreen != AppScreen.HOME) {
                    viewModel.setScreen(AppScreen.HOME)
                }

                Scaffold(
                    snackbarHost = {
                        SnackbarHost(
                            hostState = snackbarHostState,
                            snackbar = { data ->
                                Snackbar(
                                    snackbarData = data,
                                    containerColor = Color(0xFF1C1E28),
                                    contentColor = Color.White
                                )
                            }
                        )
                    },
                    topBar = {
                        FloatingCapsuleHeader(
                            currentScreen = uiState.currentScreen,
                            onScreenSelect = { viewModel.setScreen(it) },
                            onImportClick = { viewModel.toggleImportSheet(true) },
                            onExportClick = { viewModel.toggleExportSheet(true) }
                        )
                    },
                    containerColor = BlackBackground
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        AnimatedContent(
                            targetState = uiState.currentScreen,
                            transitionSpec = {
                                fadeIn() togetherWith fadeOut()
                            },
                            label = "screen_transition"
                        ) { screen ->
                            when (screen) {
                                AppScreen.HOME -> HomeScreen(
                                    uiState = uiState,
                                    onNavigate = { viewModel.setScreen(it) },
                                    onSelectSkill = { viewModel.selectSkill(it) },
                                    onOpenImport = { viewModel.toggleImportSheet(true) },
                                    onOpenExport = { viewModel.toggleExportSheet(true) },
                                    onShowStatus = { viewModel.showStatus(it) }
                                )
                                AppScreen.CONVERT -> ConvertScreen(
                                    uiState = uiState,
                                    viewModel = viewModel,
                                    onShowStatus = { viewModel.showStatus(it) }
                                )
                                AppScreen.EDITOR -> EditorScreen(
                                    uiState = uiState,
                                    viewModel = viewModel,
                                    onShowStatus = { viewModel.showStatus(it) }
                                )
                                AppScreen.EXPLORER -> ExplorerScreen(
                                    uiState = uiState,
                                    viewModel = viewModel,
                                    onShowStatus = { viewModel.showStatus(it) }
                                )
                            }
                        }
                    }
                }

                // Bottom Sheets
                if (uiState.isImportSheetVisible) {
                    ImportModalSheet(
                        viewModel = viewModel,
                        onDismiss = { viewModel.toggleImportSheet(false) }
                    )
                }

                if (uiState.isExportSheetVisible) {
                    ExportModalSheet(
                        uiState = uiState,
                        onDismiss = { viewModel.toggleExportSheet(false) }
                    )
                }

                if (uiState.isAddNodeSheetVisible) {
                    AddNodeModalSheet(
                        onSelectKind = { viewModel.addNode(it) },
                        onDismiss = { viewModel.toggleAddNodeSheet(false) }
                    )
                }
            }
        }
    }
}
