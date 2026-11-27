package com.nexvora.echora.ui.echora

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexvora.echora.di.AppContainer
import com.nexvora.echora.feature.edit.EditScreen
import com.nexvora.echora.feature.edit.EditorOpenRequest
import com.nexvora.echora.feature.edit.consumeEditorOpenRequest
import com.nexvora.echora.feature.timeline.TimelineScreen
import com.nexvora.echora.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Route(val label: String, val icon: ImageVector? = null) {
    Home("Home", Icons.Outlined.Home), Studio("Studio", Icons.Outlined.GraphicEq), Library("Library", Icons.Outlined.LibraryMusic),
    Premium("Premium", Icons.Outlined.WorkspacePremium), Settings("Settings", Icons.Outlined.Settings),
    Recorder("Recorder"), Editor("Audio Editor"), Multitrack("Multitrack"), Transcribe("Transcription"), Models("Model Manager"),
    Noise("Noise Reduction"), Convert("Convert & Export"), Payment("Payment"), PaymentHistory("Payment History"), Privacy("Privacy");
    val topLevel get() = icon != null
}

/** Applies a settings change from any screen (persisted in DataStore). */
fun AppContainer.updateSettings(block: (com.nexvora.echora.settings.EchoraSettings) -> com.nexvora.echora.settings.EchoraSettings) {
    appScope.launch { settingsRepository.update(block, settings.value) }
}

@Composable
fun EchoraRoot(container: AppContainer) {
    val settings by container.settings.collectAsStateWithLifecycle()
    var splashDone by rememberSaveable { mutableStateOf(false) }
    var stackStr by rememberSaveable { mutableStateOf(Route.Home.name) }
    val stack = stackStr.split(",").map(Route::valueOf)
    val current = stack.last()
    fun go(r: Route) { stackStr = if (r.topLevel) r.name else "$stackStr,${r.name}" }
    fun back() { stackStr = if (stack.size > 1) stack.dropLast(1).joinToString(",") { it.name } else Route.Home.name }
    var editorUri by rememberSaveable { mutableStateOf<String?>(null) }
    var editorId by rememberSaveable { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) { delay(900); splashDone = true }
    BackHandler(enabled = stack.size > 1 || current != Route.Home) { if (stack.size > 1) back() else stackStr = Route.Home.name }

    EchoraBackground {
        when {
            !splashDone -> SplashScreen()
            !settings.onboardingDone -> OnboardingScreen(onFinish = { container.updateSettings { it.copy(onboardingDone = true) } })
            else -> Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    AnimatedVisibility(current.topLevel) {
                        NavigationBar(containerColor = InkRaised) {
                            Route.entries.filter { it.topLevel }.forEach { r ->
                                NavigationBarItem(selected = current == r, onClick = { stackStr = r.name }, icon = { Icon(r.icon!!, r.label) }, label = { Text(r.label) },
                                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.White, selectedTextColor = Echora.Cyan, indicatorColor = Echora.Purple, unselectedIconColor = TextLo, unselectedTextColor = TextLo))
                            }
                        }
                    }
                },
            ) { pad ->
                Box(Modifier.padding(pad)) {
                    when (current) {
                        Route.Home -> HomeScreen(container, ::go) { uri -> editorId++; editorUri = uri; go(Route.Editor) }
                        Route.Studio -> StudioScreen(::go)
                        Route.Library -> LibraryHub(container, onTranscribe = { go(Route.Transcribe) }, onEdit = { uri -> editorId++; editorUri = uri.toString(); go(Route.Editor) })
                        Route.Premium -> PremiumScreen(container, onUpgrade = { go(Route.Payment) }, onHistory = { go(Route.PaymentHistory) })
                        Route.Settings -> EchoraSettingsScreen(container, onModels = { go(Route.Models) }, onPrivacy = { go(Route.Privacy) })
                        Route.Recorder -> RecorderScreen(container, onBack = ::back, onTranscribe = { go(Route.Transcribe) }, onLibrary = { stackStr = Route.Library.name })
                        Route.Editor -> Column {
                            EchoraTopBar("Audio Editor", ::back)
                            EditScreen(
                                waveformExtractor = container.waveformExtractor, ffmpeg = container.ffmpeg, recorder = container.recordingController,
                                playerFactory = container.playerFactory, initialUri = editorUri?.let(Uri::parse), initialUriRequestId = editorId,
                                onInitialUriAccepted = { acc -> val pend = editorUri?.let { EditorOpenRequest(editorId, it) }; editorUri = consumeEditorOpenRequest(pend, acc)?.uri },
                            )
                        }
                        Route.Multitrack -> TimelineScreen(waveformExtractor = container.waveformExtractor, ffmpeg = container.ffmpeg, session = container.timelineSession, onBack = ::back)
                        Route.Transcribe -> TranscribeScreen(container, onBack = ::back, onModels = { go(Route.Models) }, onPremium = { go(Route.Premium) })
                        Route.Models -> ModelManagerScreen(container, onBack = ::back)
                        Route.Noise -> NoiseScreen(container, onBack = ::back, onPremium = { go(Route.Premium) })
                        Route.Convert -> ConvertScreen(container, onBack = ::back, onPremium = { go(Route.Premium) })
                        Route.Payment -> PaymentScreen(container, onBack = ::back, onHistory = { go(Route.PaymentHistory) })
                        Route.PaymentHistory -> PaymentHistoryScreen(container, onBack = ::back)
                        Route.Privacy -> PrivacyScreen(container, onBack = ::back)
                    }
                }
            }
        }
    }
}

@Composable
private fun SplashScreen() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        MicOrb(0f, true, size = 130.dp)
        Spacer(Modifier.height(16.dp))
        Text("NEXVORA", color = TextHi, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
        Text("ECHORA", color = Echora.Cyan, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 6.sp)
        Text("Your Voice. Your Intelligence. Your Creativity.", color = TextLo, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun OnboardingScreen(onFinish: () -> Unit) {
    val pages = listOf(
        Triple("Record & edit", "Capture lossless WAV audio, even with the screen off, then trim, split, merge, fade and clean it up.", Icons.Filled.Mic),
        Triple("Offline-first AI", "Download a Whisper model once. Bangla and English transcription then runs entirely on your device — no upload.", Icons.Outlined.Translate),
        Triple("Your privacy", "Audio is only sent to an online service if you choose Online mode and accept a clear notice each time you change that setting.", Icons.Outlined.Lock),
    )
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(24.dp).systemBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(pager, Modifier.weight(1f)) { i ->
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(pages[i].third, null, tint = Echora.Cyan, modifier = Modifier.size(96.dp))
                Spacer(Modifier.height(24.dp))
                Text(pages[i].first, color = TextHi, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(pages[i].second, color = TextMid, textAlign = TextAlign.Center, fontSize = 16.sp)
            }
        }
        Text("Microphone access is requested only when you start a recording.", color = TextLo, fontSize = 12.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        GradientButton(if (pager.currentPage == pages.lastIndex) "Get started" else "Next", onClick = {
            if (pager.currentPage == pages.lastIndex) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
        }, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = onFinish) { Text("Skip", color = TextLo) }
    }
}
