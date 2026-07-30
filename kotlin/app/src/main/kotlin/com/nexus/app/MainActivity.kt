package com.nexus.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.nexus.app.growth.GrowthScreen
import com.nexus.app.health.HealthConnectManager
import com.nexus.app.health.HealthSyncWorker
import com.nexus.app.health.SyncSelfHeal
import com.nexus.app.home.AppOpenTracker
import com.nexus.app.home.HomeScreen
import com.nexus.app.home.WelcomeBackScene
import com.nexus.app.onboarding.InitialLevelScene
import com.nexus.app.onboarding.OnboardingScreen
import com.nexus.app.onboarding.OnboardingStore
import com.nexus.app.settings.SettingsScreen
import com.nexus.app.steps.ActivityScreen
import com.nexus.app.telemetry.Telemetry
import com.nexus.app.telemetry.TelemetryEvent
import com.nexus.app.ui.NexusIcons
import com.nexus.app.ui.NexusMotion
import com.nexus.app.ui.NexusSpacing
import com.nexus.app.ui.NexusTheme
import com.nexus.app.ui.TabIcon
import com.nexus.app.ui.motionDuration
import com.nexus.core.ActivityType
import com.nexus.core.HealthAvailability
import com.nexus.core.ReturnWelcomePolicy
import com.nexus.core.XpEngine
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 콜드스타트 스플래시 (#261) — super.onCreate 전에 설치해 Theme.Nexus.Starting을 Theme.Nexus로 전환.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // 시스템바 아이콘 대비를 라이트/다크에 맞게 자동 동기화 (#64 — targetSdk 36 edge-to-edge)
        enableEdgeToEdge()
        // 앱 열림은 화면 진입에서만 — Application.onCreate는 워커 기동도 지나가 분모가 오염되고,
        // savedInstanceState 가드가 회전 재생성 중복을 막는다 (#46 리뷰 F1)
        if (savedInstanceState == null) {
            Telemetry.record(TelemetryEvent.APP_OPENED)
        }
        val manager = HealthConnectManager(applicationContext)
        setContent {
            NexusTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NexusApp(manager)
                }
            }
        }
    }

    /**
     * 오픈 날짜 기록 (#286) — 콜드스타트뿐 아니라 **전면 복귀마다** 남긴다. 컴포지션 1회 이펙트에
     * 얹으면 웜 리줌·프로세스 복원에서 날이 누락돼 게이트가 과소 집계된다(#286 리뷰).
     * 값은 기기 로컬에만 쌓이고 계측·서버로 가지 않는다.
     */
    override fun onResume() {
        super.onResume()
        AppOpenTracker(applicationContext).recordOpenDay()
        // 백그라운드 배관 자가복구 (#237) — 주기 워커가 무흔적 사망하면 재등록 지점이 없었다.
        // 여기(전면 복귀)가 유일하게 확실한 기회다. 조용히 세우고 UI엔 아무 말도 하지 않는다.
        SyncSelfHeal.onForeground(
            applicationContext,
            connected = OnboardingStore(applicationContext).connected,
        )
    }
}

@Composable
private fun NexusApp(manager: HealthConnectManager) {
    val context = LocalContext.current
    // 온보딩 완료는 영속(#44) — rememberSaveable 단독은 콜드스타트마다 온보딩을 반복하는 버그였다
    val onboarding = remember { OnboardingStore(context) }
    var finished by rememberSaveable { mutableStateOf(onboarding.completed) }
    // 온보딩 시점 불리언을 그대로 믿으면, 사용자가 설정에서 권한을 회수해도 앱은 계속 연결됐다고 본다.
    // 매 포그라운드 복귀에 실제 승인 권한으로 재파생한다 (#236) — 비차단(저장값으로 먼저 그린다).
    var connected by rememberSaveable { mutableStateOf(onboarding.connected) }
    LivePermissionSync(manager, onboarding, connected) { connected = it }
    var showInitialLevel by rememberSaveable {
        mutableStateOf(onboarding.completed && onboarding.connected && !onboarding.initialLevelShown)
    }

    // 복귀 환영 (#30): 판정·마커 갱신은 커밋된 컴포지션에서 1회(LaunchedEffect) —
    // 이니셜라이저 부수효과는 프레임 폐기 시 마커만 소모하고 판정을 잃을 수 있다.
    // rememberSaveable(-1 = 미판정)이라 회전·프로세스 복원에도 같은 판정이 유지된다.
    val tracker = remember { AppOpenTracker(context) }
    var welcomeGapDays by rememberSaveable { mutableStateOf(UNDECIDED_GAP) }
    LaunchedEffect(Unit) {
        if (welcomeGapDays == UNDECIDED_GAP) {
            val today = LocalDate.now().toEpochDay()
            val last = tracker.lastOpenEpochDay
            tracker.recordOpen(today)
            welcomeGapDays =
                if (ReturnWelcomePolicy.shouldWelcome(last, today)) {
                    ReturnWelcomePolicy.gapDays(last, today)
                } else {
                    0L
                }
        }
    }

    if (!finished) {
        OnboardingScreen(manager) { isConnected ->
            connected = isConnected
            finished = true
            onboarding.completed = true
            onboarding.connected = isConnected
            // 첫 세션 루프(#211)는 여기를 지난 사용자만 대상 — 기존 설치는 온보딩을 다시 밟지 않는다
            onboarding.firstSessionEligible = true
            Telemetry.recordOnce(context, TelemetryEvent.ONBOARDING_COMPLETED)
            if (isConnected) {
                Telemetry.recordOnce(context, TelemetryEvent.PERMISSION_GRANTED)
                // 연결 성공 시 15분 주기 백그라운드 동기화 등록 (#8) + 초기 레벨 연출 (#44)
                HealthSyncWorker.enqueuePeriodic(context)
                showInitialLevel = !onboarding.initialLevelShown
            }
        }
    } else if (connected && showInitialLevel) {
        // 최초 연결 직후 1회 — 과거 이력 소급 "이미 이만큼 성장" (#44)
        InitialLevelScene(manager) { markShown ->
            // 일시 실패면 기록하지 않아 다음 실행에서 연출 재시도 (#44 리뷰 F1)
            if (markShown) onboarding.initialLevelShown = true
            showInitialLevel = false
        }
    } else if (connected && welcomeGapDays > 0L) {
        // 3일+ 공백 복귀 → 환영 씬 먼저 (#30, 1급 기능)
        WelcomeBackScene(gapDays = welcomeGapDays, onContinue = { welcomeGapDays = 0L })
    } else if (connected) {
        // 연결됨 → 홈/활동/성장 3탭 (#23·#32).
        ConnectedTabs(manager, onReconnect = { finished = false })
    } else {
        DemoLanding(
            availability = manager.availability(),
            onReconnect = { finished = false },
        )
    }
}

/** 복귀 판정 전 표식 — 온보딩이 먼저 렌더되므로 사용자에게 보이는 지연은 없다 (#30). */
private const val UNDECIDED_GAP = -1L

private enum class MainTab(val labelRes: Int, val icon: TabIcon) {
    HOME(R.string.tab_home, NexusIcons.home),
    ACTIVITY(R.string.tab_activity, NexusIcons.activity),
    GROWTH(R.string.tab_growth, NexusIcons.growth),
    SETTINGS(R.string.tab_settings, NexusIcons.settings),
}

@Composable
private fun ConnectedTabs(manager: HealthConnectManager, onReconnect: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    val stateHolder = rememberSaveableStateHolder()
    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {
                            Icon(
                                painter = painterResource(if (tab == t) t.icon.filled else t.icon.outline),
                                // 라벨이 상시 표시돼 접근성 이름을 제공 — CD는 null로 두어 이중 낭독 방지(#255 리뷰)
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(t.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        // 탭 전환 크로스페이드 (#262) — SaveableStateProvider로 각 탭의 스크롤 위치 보존(재진입 시 복원),
        // 루트 fillMaxSize. duration은 motionDuration 경유(리듀스드모션 시 0=즉시).
        Crossfade(
            targetState = tab,
            animationSpec = tween(motionDuration(NexusMotion.DURATION_MEDIUM), easing = NexusMotion.Standard),
            label = "tab",
            modifier = Modifier.fillMaxSize(),
        ) { current ->
            stateHolder.SaveableStateProvider(current) {
                val screenModifier = Modifier.padding(padding)
                when (current) {
                    MainTab.HOME -> HomeScreen(manager, screenModifier, onReconnect)
                    MainTab.ACTIVITY -> ActivityScreen(manager, screenModifier, onReconnect)
                    MainTab.GROWTH -> GrowthScreen(manager, screenModifier, onReconnect)
                    MainTab.SETTINGS -> SettingsScreen(manager, screenModifier, onReconnect)
                }
            }
        }
    }
}

/** 권한 거부·HC 미가용 시 데모 랜딩. 실제 홈 화면은 E4에서 대체. */
@Composable
internal fun DemoLanding(availability: HealthAvailability, onReconnect: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(NexusSpacing.screen),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 3상태 분기 (#236) — UpdateRequired는 '불가'가 아니라 '업데이트하면 됨'이다
        val titleRes = when (availability) {
            HealthAvailability.Available -> R.string.status_demo_title
            HealthAvailability.UpdateRequired -> R.string.status_update_required_title
            HealthAvailability.Unavailable -> R.string.status_unavailable_title
        }
        val bodyRes = when (availability) {
            HealthAvailability.Available -> R.string.status_demo_body
            HealthAvailability.UpdateRequired -> R.string.status_update_required_body
            HealthAvailability.Unavailable -> R.string.status_unavailable_body
        }
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(NexusSpacing.sm))
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        if (availability == HealthAvailability.UpdateRequired) {
            Spacer(Modifier.height(NexusSpacing.lg))
            // 업데이트 딥링크 — 스토어의 HC 페이지로. 실패해도 아래 재연결 버튼이 남는다.
            Button(onClick = { openHealthConnectListing(context) }) {
                Text(stringResource(R.string.action_update_health_connect))
            }
        }
        if (availability == HealthAvailability.Available) {
            Spacer(Modifier.height(NexusSpacing.lg))
            Button(onClick = onReconnect) {
                Text(stringResource(R.string.action_retry_permission))
            }
        }
        Spacer(Modifier.height(NexusSpacing.xl))
        // core(KMP) 연결 증명 — 성장 미리보기(실제 홈은 E4)
        val sampleXp = XpEngine.baseScore(ActivityType.RUNNING, minutes = 30)
        Text(
            text = stringResource(R.string.growth_preview, sampleXp, XpEngine.FORMULA_VERSION),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * Health Connect 스토어 페이지 열기 (#236) — 구버전 HC의 유일한 출구.
 *
 * 딥링크가 막힌 기기(스토어 없음·정책)에서도 앱이 죽지 않게 실패를 흡수한다 — 그 경우에도 화면의
 * 안내 문구는 남으므로 사용자는 무엇을 해야 하는지 안다.
 */
private fun openHealthConnectListing(context: android.content.Context) {
    val uri = android.net.Uri.parse("market://details?id=$HEALTH_CONNECT_PACKAGE")
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
    runCatching { context.startActivity(intent) }.onFailure {
        val web = android.net.Uri.parse("https://play.google.com/store/apps/details?id=$HEALTH_CONNECT_PACKAGE")
        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, web)) }
    }
}

/** HC 공급자 패키지 — 스토어 딥링크 대상. */
private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

/**
 * 연결 상태 라이브 재파생 (#236) — 매 포그라운드 복귀에 **실제 승인 권한**으로 다시 판정한다.
 *
 * 온보딩 시점 불리언을 그대로 믿으면 사용자가 설정에서 권한을 회수해도 앱은 계속 연결됐다고 본다.
 * 비차단이다: 저장값으로 먼저 그리고, 실제 값이 다를 때만 갱신한다.
 */
@Composable
private fun LivePermissionSync(
    manager: HealthConnectManager,
    onboarding: OnboardingStore,
    current: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, current) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val live = manager.hasRequiredPermissions()
            if (live != current) {
                onboarding.connected = live
                onChange(live)
            }
        }
    }
}
