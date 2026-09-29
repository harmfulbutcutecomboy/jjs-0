package com.jjs.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jjs.studio.ui.components.AnimatedSpaceBackground
import com.jjs.studio.ui.components.FloatingCapsuleHeader
import com.jjs.studio.ui.dialogs.AddNodeModalSheet
import com.jjs.studio.ui.dialogs.ExportModalSheet
import com.jjs.studio.ui.dialogs.ImportModalSheet
import com.jjs.studio.ui.screens.CodeEditorScreen
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
                        // Ambient Animated Starfield & Space Background
                        AnimatedSpaceBackground()

                        // Global parse progress overlay
                        if (uiState.isParsing) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xCC05060A)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .fillMaxWidth(0.86f)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color(0xFF12141C))
                                        .padding(22.dp)
                                ) {
                                    Text(
                                        text = "PARSING",
                                        color = Color(0xFFF7D7F8),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        text = uiState.parseLabel.ifBlank { "Working…" },
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    LinearProgressIndicator(
                                        progress = { uiState.parseProgress.coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(99.dp)),
                                        color = Color(0xFFC45EC8),
                                        trackColor = Color(0xFF2A2E38)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = "${(uiState.parseProgress * 100).toInt()}%",
                                        color = Color(0xFF8B90A0),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

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
                                    onCreateNewSkill = { viewModel.createNewSkill() },
                                    onOpenImport = { viewModel.setScreen(AppScreen.CODE_EDITOR) },
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
                                AppScreen.CODE_EDITOR -> CodeEditorScreen(
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
