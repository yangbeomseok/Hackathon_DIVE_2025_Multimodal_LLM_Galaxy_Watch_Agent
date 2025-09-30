package com.dive.weatherwatch.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.ui.semantics.*
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import com.dive.weatherwatch.ui.components.DynamicBackgroundOverlay
import androidx.wear.compose.material.Text
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import com.google.android.gms.location.LocationServices
import com.dive.weatherwatch.ui.viewmodels.TideViewModel
import com.dive.weatherwatch.ui.viewmodels.LocationViewModel
import com.dive.weatherwatch.ui.viewmodels.WeatherViewModel
import com.dive.weatherwatch.ui.viewmodels.BadaTimeViewModel
import com.dive.weatherwatch.data.*
import com.dive.weatherwatch.services.TideNotificationService
import java.text.ParseException
import java.util.regex.Pattern
import com.dive.weatherwatch.ui.theme.AppGradients
import com.dive.weatherwatch.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.Date
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.sin


// BadaTime tide 데이터를 DailyTideInfo로 변환
private fun convertBadaTimeTideData(badaTideData: List<BadaTimeTideResponse>): List<com.dive.weatherwatch.data.DailyTideInfo> {
    val today = System.currentTimeMillis()
    android.util.Log.d("TideScreen", "🔍 Analyzing BadaTime data: ${badaTideData.size} days")

    badaTideData.forEachIndexed { index, response ->
        val dateStr = response.thisDate ?: "unknown"
        android.util.Log.e("TideScreen", "🔍 BadaTime Day[$index]: $dateStr")
        android.util.Log.e("TideScreen", "🔍 BadaTime Day[$index] tideType (pMul): '${response.tideType}'")
        android.util.Log.e("TideScreen", "🔍 BadaTime Day[$index] selectedArea: '${response.selectedArea}'")

        // 미래 데이터 확인
        val datePattern = java.util.regex.Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})-.*")
        val matcher = datePattern.matcher(dateStr)
        if (matcher.matches()) {
            val year = matcher.group(1)!!.toInt()
            val month = matcher.group(2)!!.toInt()
            val day = matcher.group(3)!!.toInt()
            val calendar = java.util.Calendar.getInstance()
            calendar.set(year, month - 1, day, 0, 0, 0)
            val dayTimestamp = calendar.timeInMillis
            val todayDate = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis

            val dayDiff = (dayTimestamp - todayDate) / (24 * 60 * 60 * 1000)
            android.util.Log.e("TideScreen", "🔍 BadaTime Day[$index]: ${if (dayDiff < 0) "${kotlin.math.abs(dayDiff)}일 전" else if (dayDiff == 0L) "오늘" else "${dayDiff}일 후"}")
        }
    }

    return badaTideData.mapNotNull { tideResponse ->
        try {
            // 날짜 파싱 ("2025-8-14-목-6-21" 형식)
            val dateStr = tideResponse.thisDate ?: return@mapNotNull null
            val datePattern = Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})-.*")
            val matcher = datePattern.matcher(dateStr)

            if (!matcher.matches()) return@mapNotNull null

            val year = matcher.group(1)!!.toInt()
            val month = matcher.group(2)!!.toInt()
            val day = matcher.group(3)!!.toInt()

            val calendar = Calendar.getInstance()
            calendar.set(year, month - 1, day, 0, 0, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            // 위치명 파싱 (HTML 태그 제거)
            val locationName = tideResponse.selectedArea?.replace("<br>", "")?.trim() ?: "알 수 없는 지역"

            // 물때 정보 (실제 조위 데이터 기반으로 계산)
            android.util.Log.e("TideScreen", "🔍 convertBadaTimeTideData: 날짜='${dateStr}', 위치='${tideResponse.selectedArea}'")
            android.util.Log.e("TideScreen", "🔍 convertBadaTimeTideData: Raw tideResponse.tideType = '${tideResponse.tideType}'")
            val waterPhase = calculateWaterPhase(tideResponse) ?: "정보 없음"
            android.util.Log.e("TideScreen", "🔍 convertBadaTimeTideData: Final calculated waterPhase = '$waterPhase'")

            // 조위 이벤트 파싱
            val tideEvents = mutableListOf<com.dive.weatherwatch.data.TideEvent>()

            listOf(
                tideResponse.tideTime1,
                tideResponse.tideTime2,
                tideResponse.tideTime3,
                tideResponse.tideTime4
            ).forEach { tideTimeStr ->
                if (!tideTimeStr.isNullOrEmpty()) {
                    parseTideEvent(tideTimeStr, calendar.timeInMillis)?.let { event ->
                        tideEvents.add(event)
                    }
                }
            }

            val dailyTideInfo = com.dive.weatherwatch.data.DailyTideInfo(
                date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(calendar.timeInMillis)),
                locationName = locationName,
                tideEvents = tideEvents,
                waterPhase = waterPhase
            )

            android.util.Log.d("TideScreen", "Parsed daily tide info: $locationName, $waterPhase, ${tideEvents.size} events")
            tideEvents.forEach { event ->
                val sign = if (event.difference >= 0) "+" else ""
                android.util.Log.d("TideScreen", "  -> ${event.time} ${if (event.type == com.dive.weatherwatch.data.TideType.HIGH_TIDE) "만조" else "간조"} ${event.height}cm ($sign${event.difference})")
            }
            dailyTideInfo
        } catch (e: Exception) {
            android.util.Log.e("TideScreen", "Error parsing BadaTime tide data", e)
            null
        }
    }
}

// 조위 시간 문자열을 TideEvent로 파싱 ("05:07 (20) ▼-105" 형식)
private fun parseTideEvent(tideTimeStr: String, baseTimeMillis: Long): com.dive.weatherwatch.data.TideEvent? {
    return try {
        // 정규표현식으로 시간, 높이, 타입, 차이값 추출
        val pattern = Pattern.compile("(\\d{2}):(\\d{2})\\s+\\((\\d+)\\)\\s+([▲▼])([+-]?\\d+)")
        val matcher = pattern.matcher(tideTimeStr)

        if (matcher.find()) {
            val hour = matcher.group(1)!!.toInt()
            val minute = matcher.group(2)!!.toInt()
            val height = matcher.group(3)!!.toInt()  // 괄호 안 숫자가 실제 해수면 높이
            val direction = matcher.group(4)!!
            val difference = matcher.group(5)!!.toInt()  // 기준점 대비 차이값

            android.util.Log.d("TideScreen", "🔧 Parsing '$tideTimeStr' -> height=${height}cm, diff=${difference}, direction=$direction")

            val time = String.format("%02d:%02d", hour, minute)

            val calendar = Calendar.getInstance()
            calendar.timeInMillis = baseTimeMillis
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            val tideType = when (direction) {
                "▲" -> com.dive.weatherwatch.data.TideType.HIGH_TIDE
                "▼" -> com.dive.weatherwatch.data.TideType.LOW_TIDE
                else -> com.dive.weatherwatch.data.TideType.HIGH_TIDE
            }

            com.dive.weatherwatch.data.TideEvent(
                time = time,
                height = height,
                type = tideType,
                difference = difference,
                timestamp = calendar.timeInMillis
            )
        } else {
            null
        }
    } catch (e: Exception) {
        android.util.Log.e("TideScreen", "Error parsing tide event: $tideTimeStr", e)
        null
    }
}


// 메인 화면 컴포저블
@Composable
fun TideScreen(
    onNavigateBack: () -> Unit,
    locationViewModel: LocationViewModel = viewModel()
) {
    val context = LocalContext.current
    val tideViewModel: TideViewModel = viewModel()
    val weatherViewModel: WeatherViewModel = viewModel()
    val badaTimeViewModel: BadaTimeViewModel = viewModel()

    // BadaTime tide 데이터만 사용 (fallback 데이터 완전 제거)
    val badaTideTideData by badaTimeViewModel.tideData.collectAsState()
    val badaTimeLoading by badaTimeViewModel.isLoading.collectAsState()
    val badaTimeError by badaTimeViewModel.error.collectAsState()

    val locationName by weatherViewModel.locationName.collectAsState()
    val isLocationLoading by weatherViewModel.isLoading.collectAsState()
    val latitude by weatherViewModel.latitude.collectAsState()
    val longitude by weatherViewModel.longitude.collectAsState()

    // LocationViewModel 상태들
    val currentLocationName by locationViewModel.locationName.collectAsState()
    val currentLatitude by locationViewModel.latitude.collectAsState()
    val currentLongitude by locationViewModel.longitude.collectAsState()

    // LocationViewModel에서 WeatherViewModel로 위치 정보 동기화
    LaunchedEffect(currentLocationName, currentLatitude, currentLongitude) {
        val locationName = currentLocationName
        val latitude = currentLatitude
        val longitude = currentLongitude

        if (!locationName.isNullOrEmpty() && latitude != null && longitude != null) {
            android.util.Log.d("TideLocationSync", "Syncing location from LocationViewModel to WeatherViewModel")
            android.util.Log.d("TideLocationSync", "Location: $locationName, Lat: $latitude, Lon: $longitude")
            weatherViewModel.updateLocationName(locationName)

            // WeatherViewModel의 위치 정보도 업데이트
            val (baseDate, baseTime) = com.dive.weatherwatch.ui.screens.getValidBaseDateTime()
            weatherViewModel.fetchWeatherData(
                serviceKey = "PL6rJjVkoo/1g6B41uLFacfcit691ahg3nnJf5Ot7hJ2QlIau3oHKQub8sRNHCfp8mYIVbmYl8VBFV5s0/d2ow==",
                baseDate = baseDate,
                baseTime = baseTime,
                lat = latitude,
                lon = longitude,
                locationName = locationName
            )

            // BadaTime API도 실제 위치로 호출
            badaTimeViewModel.loadTideData(latitude, longitude)
        }
    }

    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    // 위치 권한 요청 런처
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)) {
            android.util.Log.d("TideScreen", "Location permission granted")
            // LocationViewModel을 사용하여 위치 가져오기
            locationViewModel.startLocationFetch(context, weatherViewModel)
        } else {
            android.util.Log.d("TideScreen", "Location permission denied, using default location")
            weatherViewModel.updateLocationName("부산")
        }
    }

    // 알림 권한 요청 런처
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            android.util.Log.d("TideNotification", "알림 권한 허용됨")
        } else {
            android.util.Log.d("TideNotification", "알림 권한 거부됨")
        }
    }

    // 화면 진입 시 즉시 BadaTime 로딩 시작
    LaunchedEffect(Unit) {
        android.util.Log.e("TideScreen", "🚨🚨🚨 TideScreen LaunchedEffect started!")
        android.util.Log.e("TideScreen", "🚨 Initial loading state: ${badaTimeViewModel.isLoading.value}")
        android.util.Log.e("TideScreen", "🚨 Initial data size: ${badaTimeViewModel.tideData.value.size}")

        // 즉시 BadaTime API 호출 시작 (fallback 데이터 표시 방지)
        android.util.Log.e("TideScreen", "🌊🌊🌊 === IMMEDIATE BADATIME TIDE API CALL (No fallback) ===")
        badaTimeViewModel.loadTideData(35.1796, 129.0756) // 기본 좌표로 즉시 시작

        // 위치 권한 체크 및 요청
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            android.util.Log.e("TideScreen", "🚨 Requesting location permissions")
            locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else {
            android.util.Log.e("TideScreen", "🚨 Location permission already granted")
            // LocationViewModel을 사용하여 위치 가져오기
            locationViewModel.startLocationFetch(context, weatherViewModel)
        }

        // 알림 권한 체크 및 요청 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                android.util.Log.d("TideNotification", "알림 권한 요청")
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                android.util.Log.d("TideNotification", "알림 권한 이미 허용됨")
            }
        }

        // 위치 정보는 LocationViewModel에서 자동으로 처리됨
    }

    // 조위 데이터가 업데이트될 때마다 알림 스케줄
    LaunchedEffect(badaTideTideData) {
        if (badaTideTideData.isNotEmpty()) {
            android.util.Log.d("TideNotification", "조위 데이터 ${badaTideTideData.size}개로 알림 스케줄 시작")
            // BadaTimeTideResponse를 DailyTideInfo로 변환 후 TideEvent 추출
            val convertedTideInfo = convertBadaTimeTideData(badaTideTideData)
            val tideEvents = convertedTideInfo.flatMap { it.tideEvents }
            TideNotificationService.scheduleTideNotifications(context, tideEvents)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Dynamic Background Overlay (시간 기반 배경)
        DynamicBackgroundOverlay(
            weatherData = null,
            alpha = 0.5f,
            forceTimeBasedBackground = true
        )

        // 🔍 데이터 상태 강력 로그 (BadaTime만)
        android.util.Log.e("TideScreen", "🔍🔍🔍 === DATA STATUS CHECK (BadaTime Only) ===")
        android.util.Log.e("TideScreen", "🔍 badaTimeLoading: $badaTimeLoading")
        android.util.Log.e("TideScreen", "🔍 badaTideTideData.size: ${badaTideTideData.size}")
        android.util.Log.e("TideScreen", "🔍 badaTideTideData.isEmpty(): ${badaTideTideData.isEmpty()}")
        android.util.Log.e("TideScreen", "🔍 badaTimeError: $badaTimeError")

        when {
            // BadaTime 데이터가 있으면 우선 사용
            badaTideTideData.isNotEmpty() -> {
                android.util.Log.e("TideScreen", "🔍 BRANCH: Using BadaTime data!")
                android.util.Log.e("TideScreen", "🌊 Using BadaTime tide data: ${badaTideTideData.size} items")
                val convertedTideInfo = convertBadaTimeTideData(badaTideTideData)
                android.util.Log.e("TideScreen", "🌊 Converted ${convertedTideInfo.size} daily tide info items")
                val finalLocation = locationName ?: convertedTideInfo.firstOrNull()?.locationName ?: "위치 확인 중..."
                android.util.Log.e("TideScreen", "🌊 Cards will use BadaTime data (weekly graph removed)!")
                TideWatchFace(dailyTideInfoList = convertedTideInfo, location = finalLocation, onNavigateBack = onNavigateBack)
            }
            // BadaTime 에러가 있으면 로딩 상태 (fallback 데이터 절대 사용 안 함)
            badaTimeError != null -> {
                android.util.Log.e("TideScreen", "🔍 BRANCH: Error state - but still loading")
                android.util.Log.e("TideScreen", "❌ BadaTime Error: $badaTimeError")
                // Loading animation removed
            }
            // BadaTime 로딩 중이거나 데이터가 없으면 무조건 로딩 (fallback 데이터 절대 사용 안 함)
            badaTimeLoading || badaTideTideData.isEmpty() -> {
                android.util.Log.e("TideScreen", "🔍 BRANCH: Loading state (badaTimeLoading=$badaTimeLoading, data.isEmpty=${badaTideTideData.isEmpty()})")
                // Loading animation removed
            }
            // 이 부분은 절대 도달하지 않아야 함 (fallback 완전 제거)
            else -> {
                android.util.Log.e("TideScreen", "🚨🚨🚨 UNEXPECTED BRANCH: This should never happen!")
                // Loading animation removed
            }
        }
    }
}

// 전체 워치페이스 레이아웃 - 스크롤 기반 디자인
@Composable
private fun TideWatchFace(dailyTideInfoList: List<DailyTideInfo>, location: String, onNavigateBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showTideInfo by remember { mutableStateOf(false) }
    var showWaterPhaseInfo by remember { mutableStateOf(false) }
    var showTestNotification by remember { mutableStateOf<TestNotificationType?>(null) }
    var selectedDayData by remember { mutableStateOf<DailyTideInfo?>(null) }
    
    // 시간은 고정값으로 사용하여 불필요한 recomposition 방지
    val currentTime = remember { Date() }
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "조위 화면. 좌우 가장자리를 터치하면 이전 화면으로 돌아갑니다."
                role = Role.Button
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        if (offset.x < size.width * 0.15f || offset.x > size.width * 0.85f) {
                            onNavigateBack()
                        }
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 메인 화면 (2x2 그리드)
            MainTideView(
                dailyTideInfoList = dailyTideInfoList,
                location = location,
                currentTime = currentTime,
                onWaterPhaseClick = { selectedDay ->
                    selectedDayData = selectedDay
                    showWaterPhaseInfo = true
                }
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 상세 그래프 뷰는 제거됨 - 주간 그래프 기능 삭제
            // DetailTideGraphView(weeklyTideInfo, currentTime)

            Spacer(modifier = Modifier.height(8.dp))

            // 알림 테스트 버튼
            TestNotificationButton(
                context = context,
                onHighTideTest = { showTestNotification = TestNotificationType.HIGH_TIDE },
                onLowTideTest = { showTestNotification = TestNotificationType.LOW_TIDE }
            )
        }

        // 조위 정보 상세 팝업
        if (showTideInfo) {
            TideInfoPopup(
                dailyTideInfoList = dailyTideInfoList,
                onDismiss = { showTideInfo = false }
            )
        }

        // 물때 정보 팝업 (전체 화면 팝업)
        if (showWaterPhaseInfo) {
            WaterPhaseInfoPopup(
                selectedDayData = selectedDayData,
                onDismiss = { showWaterPhaseInfo = false }
            )
        }

        // 테스트 알림 팝업
        showTestNotification?.let { notificationType ->
            TestNotificationPopup(
                notificationType = notificationType,
                onDismiss = { showTestNotification = null }
            )
        }
    }
}

// 상세 조위 그래프 뷰 (주간 그래프 기능 제거로 사용 안 함)
/*
@Composable
private fun DetailTideGraphView(weeklyTideInfo: WeeklyTideDisplayInfo, currentTime: Date) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 상세 그래프 제목
        Text(
            text = "일주일 조위 그래프",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        // 메인 그래프
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp) // 고정 높이
        ) {
            // 은은한 중앙 링 효과 (중복 방지를 위해 제거)
            // Canvas가 중복되어 텍스트가 겹치는 문제 해결

            // 일주일 조수 그래프 (현재 weeklyTideInfo.weeklyData 사용 중)
            android.util.Log.d("TideScreen", "📊 Graph using data source: ${if (weeklyTideInfo.weeklyData.isEmpty()) "EMPTY" else "BadaTime converted data (${weeklyTideInfo.weeklyData.size} days)"}")
            // WeeklyTideChart(weeklyData = weeklyTideInfo.weeklyData) // 주간 그래프 제거됨
        }

        Spacer(modifier = Modifier.height(16.dp))

    }
}
*/

// 상세 조위 그래프 뷰 (주간 그래프 기능 제거됨 - 더 이상 사용하지 않음)
/*
@Composable
private fun DetailTideView(weeklyTideInfo: WeeklyTideDisplayInfo, currentTime: Date, onNavigateBack: () -> Unit) {
    // 주간 조위 그래프 제거로 인해 이 함수는 더 이상 사용되지 않음
    // 기존 구현은 WeeklyTideChart와 weeklyData에 의존했으나, 이는 모두 제거됨
}
*/

// 상단 헤더
@Composable
private fun TopHeader(displayTime: String, location: String, dayOfWeek: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = " $displayTime, $dayOfWeek", color = Color.LightGray.copy(alpha = 1f), fontSize = 10.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = location, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

// TimePoint 데이터 클래스
data class TimePoint(
    val hour: Int,
    val level: Float,
    val timestamp: Long,
    val actualHeight: Float = 0f,
    val isCurrentTime: Boolean = false
)

// 실제 조위 데이터를 기반으로 정확한 물때 계산
private fun calculateWaterPhase(tideResponse: BadaTimeTideResponse): String? {
    android.util.Log.e("TideScreen", "🔍 calculateWaterPhase 호출됨")
    android.util.Log.e("TideScreen", "🔍 tideResponse.tideType (pMul): '${tideResponse.tideType}'")

    // API에서 받은 pMul 데이터를 우선 사용
    val apiWaterPhase = tideResponse.tideType
    if (!apiWaterPhase.isNullOrEmpty()) {
        android.util.Log.e("TideScreen", "🔍 API pMul 데이터 사용: '$apiWaterPhase'")
        return apiWaterPhase
    }

    android.util.Log.e("TideScreen", "🔍 API pMul 데이터가 없어서 계산으로 대체")

    val timeNow = System.currentTimeMillis()
    val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

    // 오늘의 조위 이벤트들 파싱
    val tideEvents = mutableListOf<Triple<Int, Int, String>>() // (hour, height, type)

    listOf(
        tideResponse.tideTime1,
        tideResponse.tideTime2,
        tideResponse.tideTime3,
        tideResponse.tideTime4
    ).forEach { tideTimeStr ->
        if (!tideTimeStr.isNullOrEmpty()) {
            try {
                val pattern = Pattern.compile("(\\d{2}):(\\d{2})\\s+\\((\\d+)\\)\\s+([▲▼])([+-]?\\d+)")
                val matcher = pattern.matcher(tideTimeStr)
                if (matcher.find()) {
                    val hour = matcher.group(1)!!.toInt()
                    val height = matcher.group(3)!!.toInt()
                    val direction = matcher.group(4)!!
                    val type = if (direction == "▲") "만조" else "간조"
                    tideEvents.add(Triple(hour, height, type))
                }
            } catch (e: Exception) {
                android.util.Log.e("TideScreen", "Error parsing tide time: $tideTimeStr", e)
            }
        }
    }

    // 현재 시간 기준으로 물때 계산
    return if (tideEvents.isNotEmpty()) {
        // 만조와 간조의 높이 차이로 조류 세기 판단
        val highTides = tideEvents.filter { it.third == "만조" }.map { it.second }
        val lowTides = tideEvents.filter { it.third == "간조" }.map { it.second }

        if (highTides.isNotEmpty() && lowTides.isNotEmpty()) {
            val maxHigh = highTides.maxOrNull() ?: 0
            val minLow = lowTides.minOrNull() ?: 0
            val tidalRange = maxHigh - minLow

            // 현재 시간에 가장 가까운 조위 이벤트 찾기
            val closestEvent = tideEvents.minByOrNull { kotlin.math.abs(it.first - currentHour) }
            val hourDiff = if (closestEvent != null) kotlin.math.abs(closestEvent.first - currentHour) else 12

            android.util.Log.d("TideScreen", "물때 계산: tidalRange=$tidalRange, hourDiff=$hourDiff, closestEvent=${closestEvent?.third}")

            // 조차(조위 차이)와 시간을 기반으로 물때 계산
            when {
                tidalRange > 80 -> { // 대조차 (강한 조류)
                    when {
                        hourDiff <= 1 && closestEvent?.third == "만조" -> "11물" // 만조 직전/직후 대조기
                        hourDiff <= 1 && closestEvent?.third == "간조" -> "4물"  // 간조 직전/직후 대조기
                        hourDiff <= 2 -> "10물"  // 대조기
                        hourDiff <= 3 -> "9물"   // 대조기 접근
                        else -> "8물"
                    }
                }
                tidalRange < 40 -> { // 소조차 (약한 조류)
                    when {
                        hourDiff <= 1 -> "7물"   // 소조기 정점
                        hourDiff <= 2 -> "6물"   // 소조기
                        else -> "5물"
                    }
                }
                else -> { // 중간 조차
                    when {
                        hourDiff <= 1 && closestEvent?.third == "만조" -> "3물"
                        hourDiff <= 1 && closestEvent?.third == "간조" -> "12물"
                        hourDiff <= 2 -> "2물"
                        else -> "1물"
                    }
                }
            }
        } else {
            "정보 없음"
        }
    } else {
        "정보 없음"
    }
}


// 로딩 애니메이션
@Composable
private fun     LoadingAnimation() {
    var dotCount by remember { mutableStateOf(1) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(500) // 0.5초마다 변경
            dotCount = if (dotCount >= 3) 1 else dotCount + 1
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 로딩 화면 배경
        DynamicBackgroundOverlay(
            weatherData = null,
            alpha = 0.7f,
            forceTimeBasedBackground = true
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
        Image(
            painter = painterResource(id = R.mipmap.water),
            contentDescription = "Water",
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
                text = "조위 데이터  로딩중" + ".".repeat(dotCount),
            color = Color.White,
            fontSize = 14.sp
        )
        }
    }
}


@Composable
private fun TideInfoPopup(
    dailyTideInfoList: List<DailyTideInfo>,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(
                    Color.Black.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(16.dp)
        ) {
            val currentWaterPhase = dailyTideInfoList.firstOrNull()?.waterPhase ?: "정보 없음"
            val todayTideEvents = dailyTideInfoList.firstOrNull()?.tideEvents ?: emptyList()
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 제목
                Text(
                    text = "🌊 조위 정보",
                    color = Color(0xFF00D4FF),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                
                // 현재 물때 설명
                Text(
                    text = "현재: $currentWaterPhase",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                
                // 물때 설명
                Text(
                    text = getWaterPhaseDescription(currentWaterPhase),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                
                // 오늘의 만조/간조 정보
                if (todayTideEvents.isNotEmpty()) {
                    Text(
                        text = "오늘의 조위",
                        color = Color(0xFF00D4FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    
                    todayTideEvents.take(4).forEach { event ->
                        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(event.timestamp))
                        val typeStr = when (event.type) {
                            com.dive.weatherwatch.data.TideType.HIGH_TIDE -> "만조"
                            com.dive.weatherwatch.data.TideType.LOW_TIDE -> "간조"
                        }
                        android.util.Log.d("TideScreen", "📋 Popup showing: $timeStr $typeStr ${event.height}cm")
                        Text(
                            text = "$timeStr  $typeStr  ${event.height}cm",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 10.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // 닫기 안내
                Text(
                    text = "화면을 터치하여 닫기",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 8.sp
                )
            }
        }
    }
}

private fun getWaterPhaseDescription(waterPhase: String): String {
    // 디버깅용 로그
    android.util.Log.e("TideScreen", "🔍 getWaterPhaseDescription: input waterPhase = '$waterPhase'")
    
    return when {
        waterPhase.contains("1물") || waterPhase.contains("1") -> """대조기 시작, 조류가 강해집니다
• 권장 낚시: 찌낚시, 원투낚시
• 추천 어종: 우럭, 학꽁치, 전어
• 팁: 채비를 무겁게 하세요"""
        
        waterPhase.contains("2물") || waterPhase.contains("2") -> """조류가 점점 강해지는 시간
• 권장 낚시: 루어낚시, 선상낚시
• 추천 어종: 농어, 참돔, 볼락
• 팁: 액션이 강한 루어 사용"""
        
        waterPhase.contains("3물") || waterPhase.contains("3") -> """조류가 강한 대물 타임!
• 권장 낚시: 지깅, 타이라바
• 추천 어종: 부시리, 방어, 참돔  
• 팁: 무거운 지그로 바닥층 공략"""
        
        waterPhase.contains("4물") || waterPhase.contains("4") -> """조류가 약해 초보자에게 최적
• 권장 낚시: 갯바위 찌낚시
• 추천 어종: 감성돔, 벵에돔, 우럭
• 팁: 예민한 찌를 사용하세요"""
        
        waterPhase.contains("5물") || waterPhase.contains("5") -> """조류가 매우 약한 시간
• 권장 낚시: 바닥낚시, 원투낚시
• 추천 어종: 도다리, 가자미, 망둥어
• 팁: 밑밥을 적극 활용하세요"""
        
        waterPhase.contains("6물") || waterPhase.contains("6") -> """소조기, 조류가 가장 약합니다
• 권장 낚시: 릴찌낚시, 민장대
• 추천 어종: 붕어, 잉어, 소형 어종
• 팁: 섬세한 채비로 입질 파악"""
        
        waterPhase.contains("7물") || waterPhase.contains("7") -> """조류가 다시 강해지기 시작
• 권장 낚시: 찌낚시, 선상낚시
• 추천 어종: 고등어, 전갱이, 삼치
• 팁: 채비를 점차 무겁게 교체"""
        
        waterPhase.contains("8물") || waterPhase.contains("8") -> """조류가 강해지는 시간
• 권장 낚시: 루어낚시, 지깅
• 추천 어종: 농어, 시마노, 광어
• 팁: 중층 ~ 바닥층 집중 공략"""
        
        waterPhase.contains("9물") || waterPhase.contains("9") -> """조류가 매우 강한 시간
• 권장 낚시: 무거운 지깅, 타이라바
• 추천 어종: 대형 어종, 회유성 어류
• 팁: 안전에 각별히 주의하세요"""
        
        waterPhase.contains("10물") || waterPhase.contains("10") -> """대조기 절정! 골든타임
• 권장 낚시: 대물 노리는 지깅
• 추천 어종: 부시리, 방어, 참치류
• 팁: 드랙을 단단히 조이세요"""
        
        waterPhase.contains("11물") || waterPhase.contains("11") -> """조류 강하나 점차 약해짐
• 권장 낚시: 찌낚시, 선상낚시
• 추천 어종: 참돔, 돌돔, 농어
• 팁: 만조 시간대를 놓치지 마세요"""
        
        waterPhase.contains("12물") || waterPhase.contains("12") -> """조류가 약해지기 시작
• 권장 낚시: 갯바위 찌낚시
• 추천 어종: 감성돔, 벵에돔, 볼락
• 팁: 예민한 찌로 입질 감지"""
        
        waterPhase.contains("13물") || waterPhase.contains("13") -> """조류가 약해지는 시간
• 권장 낚시: 바닥낚시, 원투낚시
• 추천 어종: 우럭, 학꽁치, 넙치
• 팁: 채비를 가볍게 조정하세요"""
        
        waterPhase.contains("14물") || waterPhase.contains("14") -> """조류가 약해진 상태
• 권장 낚시: 민장대, 릴찌낚시
• 추천 어종: 소형 어종, 바닥 어류
• 팁: 밑밥으로 어군 형성"""
        
        waterPhase.contains("15물") || waterPhase.contains("15") -> """조류 약해 초보자에게 안성맞춤
• 권장 낚시: 갯바위, 방파제 낚시
• 추천 어종: 망둥어, 학꽁치, 소형 우럭
• 팁: 차분한 낚시로 기술 연마"""
        
        waterPhase.contains("조금") -> """소조기, 조류가 가장 약합니다
• 권장 낚시: 바닥낚시 전문
• 추천 어종: 가자미, 도다리, 넙치
• 팁: 바닥 지형 변화를 노려보세요"""
        
        waterPhase.contains("대조") -> """대조기, 대물 찬스!
• 권장 낚시: 지깅, 큰 바늘 채비
• 추천 어종: 방어, 부시리, 참치류
• 팁: 안전장비 필수, 드랙 체크"""
        
        else -> """• 만조/간조 시간 확인
• 조류 강도 파악  
• 어종별 최적 타이밍 추천"""
    }
}

// 새로운 메인 물때 정보 뷰 (2x2 그리드)
@Composable
private fun MainTideView(
    dailyTideInfoList: List<DailyTideInfo>,
    location: String,
    currentTime: Date,
    onWaterPhaseClick: (DailyTideInfo?) -> Unit
) {
    // 선택된 날짜 인덱스 상태 관리
    var selectedDateIndex by remember { mutableStateOf(0) }
    
    // 데이터가 있는 경우에만 진행
    val sortedDailyTideInfoList = remember(dailyTideInfoList) {
        dailyTideInfoList.sortedBy { it.date }
    }
    
    // 오늘 날짜에 해당하는 인덱스 찾기
    LaunchedEffect(sortedDailyTideInfoList) {
        if (sortedDailyTideInfoList.isNotEmpty()) {
            val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val todayIndex = sortedDailyTideInfoList.indexOfFirst { it.date == todayDateStr }
            selectedDateIndex = if (todayIndex >= 0) todayIndex else 0
        }
    }
    
    // 현재 선택된 날짜의 데이터
    val selectedDayData = sortedDailyTideInfoList.getOrNull(selectedDateIndex)
    
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 상단 헤더 (시간, 날짜, 위치 + 물때)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 0.dp)
        ) {
            // 현재 시간만 표시
            Text(
                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(currentTime),
                color = Color.White,
                fontSize = 8.sp,
            )
            
            Spacer(modifier = Modifier.height(2.dp))
            
            // 위치 (첫 번째 줄)
            Text(
                text = location,
                color = Color.White,
                fontSize = 8.sp,
                fontWeight = FontWeight.Light
            )
            
            Spacer(modifier = Modifier.height(1.dp))
            
        }
        
        Spacer(modifier = Modifier.height(2.dp))
        
        // 날짜 선택 슬라이드 (물때 정보와 조위 카드 사이에 추가)
        if (sortedDailyTideInfoList.isNotEmpty()) {
            DateSelectorSlide(
                dailyTideInfoList = sortedDailyTideInfoList,
                selectedIndex = selectedDateIndex,
                onDateChanged = { newIndex ->
                    selectedDateIndex = newIndex
                },
                onWaterPhaseClick = onWaterPhaseClick
            )
            
            Spacer(modifier = Modifier.height(4.dp))
        }
        
        // 2x2 조위 정보 그리드 (선택된 날짜의 데이터 사용)
        val selectedTideEvents = selectedDayData?.tideEvents ?: emptyList()
        android.util.Log.d("TideScreen", "🏠 Cards using data: ${selectedTideEvents.size} events from ${selectedDayData?.locationName ?: "unknown location"} (Selected date: ${selectedDayData?.date})")
        
        // 상세 데이터 로그 (처음 4개 이벤트)
        selectedTideEvents.take(4).forEachIndexed { index, event ->
            val typeStr = if (event.type == com.dive.weatherwatch.data.TideType.HIGH_TIDE) "만조" else "간조"
            android.util.Log.d("TideScreen", "🏠 Card[$index]: $typeStr ${event.time} - ${event.height}cm (차이: ${event.difference})")
        }
        
        TideInfoGrid(selectedTideEvents.take(4))
        
        Spacer(modifier = Modifier.height(8.dp))
    }
    
}

// 날짜 선택 슬라이드 컴포넌트
@Composable
private fun DateSelectorSlide(
    dailyTideInfoList: List<DailyTideInfo>,
    selectedIndex: Int,
    onDateChanged: (Int) -> Unit,
    onWaterPhaseClick: (DailyTideInfo?) -> Unit
) {
    val selectedData = dailyTideInfoList.getOrNull(selectedIndex)
    
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 이전 날짜 버튼 (◀) - 왼쪽 끝
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(28.dp)
                .clickable(enabled = selectedIndex > 0) {
                    if (selectedIndex > 0) {
                        onDateChanged(selectedIndex - 1)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "◀",
                color = if (selectedIndex > 0) Color.White else Color.White.copy(alpha = 0.3f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        // 현재 선택된 날짜 표시 - 중앙
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center)
        ) {
            selectedData?.let { data ->
                // 날짜 파싱 및 포맷팅
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val date = remember(data.date) {
                    try {
                        dateFormat.parse(data.date)
                    } catch (e: Exception) {
                        null
                    }
                }
                val displayFormat = SimpleDateFormat("yyyy년 M월 d일 (E)", Locale.KOREAN)
                
                val formattedDate = remember(date, data.date) {
                    if (date != null) {
                        displayFormat.format(date)
                    } else {
                        data.date
                    }
                }
                
                Text(
                    text = formattedDate,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                
                // 물때 정보 표시
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable { 
                        onWaterPhaseClick(data)
                    }
                ) {
                    Canvas(modifier = Modifier.size(8.dp)) {
                        drawCircle(Color.Yellow, radius = size.width / 2)
                    }
                    
                    val waterPhase = data.waterPhase ?: "정보 없음"
                    
                    Text(
                        text = waterPhase,
                        color = Color.Yellow,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "- 물때 정보 확인",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } ?: run {
                Text(
                    text = "날짜 정보 없음",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
            }
        }
        
        // 다음 날짜 버튼 (▶) - 오른쪽 끝
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(28.dp)
                .clickable(enabled = selectedIndex < dailyTideInfoList.size - 1) {
                    if (selectedIndex < dailyTideInfoList.size - 1) {
                        onDateChanged(selectedIndex + 1)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "▶",
                color = if (selectedIndex < dailyTideInfoList.size - 1) Color.White else Color.White.copy(alpha = 0.3f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// 2x2 조위 정보 그리드
@Composable
private fun TideInfoGrid(tideEvents: List<com.dive.weatherwatch.data.TideEvent>) {
    // 4개까지만 표시하고, 부족하면 빈 칸으로 채움
    val events = tideEvents.take(4)
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 상단 행 (만조, 간조)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 좌상단
            events.getOrNull(0)?.let { event ->
                TideInfoCard(
                    event = event,
                    modifier = Modifier.weight(1f)
                )
            } ?: Box(modifier = Modifier.weight(1f))
            
            // 우상단
            events.getOrNull(1)?.let { event ->
                TideInfoCard(
                    event = event,
                    modifier = Modifier.weight(1f)
                )
            } ?: Box(modifier = Modifier.weight(1f))
        }
        
        // 하단 행
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 좌하단
            events.getOrNull(2)?.let { event ->
                TideInfoCard(
                    event = event,
                    modifier = Modifier.weight(1f)
                )
            } ?: Box(modifier = Modifier.weight(1f))
            
            // 우하단  
            events.getOrNull(3)?.let { event ->
                TideInfoCard(
                    event = event,
                    modifier = Modifier.weight(1f)
                )
            } ?: Box(modifier = Modifier.weight(1f))
        }
    }
}

// 개별 조위 정보 카드
@Composable
private fun TideInfoCard(
    event: com.dive.weatherwatch.data.TideEvent,
    modifier: Modifier = Modifier
) {
    val isHighTide = event.type == com.dive.weatherwatch.data.TideType.HIGH_TIDE
    val cardColor = if (isHighTide) Color(0xFF2196F3) else Color(0xFF4CAF50)
    val textColor = Color.White
    
    Column(
        modifier = modifier
            .background(
                cardColor.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                1.dp,
                cardColor.copy(alpha = 0.3f),
                RoundedCornerShape(8.dp)
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 만조/간조 라벨
        Text(
            text = if (isHighTide) "만조" else "간조",
            color = if (isHighTide) Color(0xFF2196F3) else Color(0xFF4CAF50),
            fontSize = 7.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .background(
                    if (isHighTide) Color(0xFF2196F3).copy(alpha = 0.2f) else Color(0xFF4CAF50).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // 시간
        Text(
            text = event.time,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(2.dp))
        
        // 높이와 차이값
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "(${event.height})",
                color = textColor.copy(alpha = 0.8f),
                fontSize = 8.sp
            )
            Text(
                text = if (isHighTide) "▲" else "▼",
                color = if (isHighTide) Color(0xFFFF5722) else Color(0xFF2196F3),
                fontSize = 8.sp
            )
            val sign = if (event.difference >= 0) "+" else ""
            Text(
                text = "$sign${event.difference}",
                color = if (event.difference >= 0) Color(0xFFFF5722) else Color(0xFF2196F3),
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// 물때 정보 상세 팝업
@Composable
private fun WaterPhaseInfoPopup(
    selectedDayData: DailyTideInfo?,
    onDismiss: () -> Unit
) {
    val waterPhase = selectedDayData?.waterPhase ?: "정보 없음"
    
    // 디버깅용 로그
    android.util.Log.e("TideScreen", "🔍 WaterPhaseInfoPopup: waterPhase = '$waterPhase'")
    android.util.Log.e("TideScreen", "🔍 WaterPhaseInfoPopup: selectedDate = '${selectedDayData?.date}'")
    
    val description = getWaterPhaseDescription(waterPhase)
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(
                    Color.Black.copy(alpha = 0.9f),
                    RoundedCornerShape(16.dp)
                )
                .border(
                    1.dp,
                    Color.White.copy(alpha = 0.3f),
                    RoundedCornerShape(16.dp)
                )
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 제목 (선택된 날짜 포함)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "물때 정보",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                
                // 선택된 날짜 표시
                selectedDayData?.let { data ->
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val date = remember(data.date) {
                        try {
                            dateFormat.parse(data.date)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    val displayFormat = SimpleDateFormat("M월 d일 (E)", Locale.KOREAN)
                    
                    val formattedDate = remember(date, data.date) {
                        if (date != null) {
                            displayFormat.format(date)
                        } else {
                            data.date
                        }
                    }
                    
                    Text(
                        text = formattedDate,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(6.dp))
            
            // 현재 물때
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Canvas(modifier = Modifier.size(12.dp)) {
                    drawCircle(Color.Yellow, radius = size.width / 2)
                }
                Text(
                    text = waterPhase,
                    color = Color.Yellow,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 설명 (분리해서 표시)
            val lines = description.split("\n")
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                lines.forEach { line ->
                    if (line.startsWith("•")) {
                        // • 기호가 있는 라인은 왼쪽 정렬
                        Text(
                            text = line,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 10.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                            lineHeight = 15.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        // • 기호가 없는 라인은 중앙 정렬
                        Text(
                            text = line,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 10.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 닫기 안내
            Text(
                text = "화면을 터치하면 닫힙니다",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 8.sp
            )
        }
    }
}

// 테스트 알림 타입
enum class TestNotificationType {
    HIGH_TIDE,
    LOW_TIDE
}

// 테스트 알림 버튼
@Composable
private fun TestNotificationButton(
    context: android.content.Context,
    onHighTideTest: () -> Unit,
    onLowTideTest: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 만조 테스트 버튼
        androidx.wear.compose.material.Button(
            onClick = {
                // 실제 알림 표시 - 만조는 14:30 (2시간 30분 후)
                val calendar = java.util.Calendar.getInstance()
                calendar.add(java.util.Calendar.HOUR_OF_DAY, 2)
                calendar.add(java.util.Calendar.MINUTE, 30)
                val futureTime = calendar.timeInMillis
                
                TideNotificationService.showTideNotification(
                    context = context,
                    tideType = com.dive.weatherwatch.data.TideType.HIGH_TIDE,
                    height = 120f,
                    tideTime = futureTime
                )
                // 팝업도 함께 표시
                onHighTideTest()
            },
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "만조 알림\n테스트",
                fontSize = 8.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        
        // 간조 테스트 버튼
        androidx.wear.compose.material.Button(
            onClick = {
                // 실제 알림 표시 - 간조는 08:15 (내일 오전)
                val calendar = java.util.Calendar.getInstance()
                calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 8)
                calendar.set(java.util.Calendar.MINUTE, 15)
                val futureTime = calendar.timeInMillis
                
                TideNotificationService.showTideNotification(
                    context = context,
                    tideType = com.dive.weatherwatch.data.TideType.LOW_TIDE,
                    height = 25f,
                    tideTime = futureTime
                )
                // 팝업도 함께 표시
                onLowTideTest()
            },
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "간조 알림\n테스트",
                fontSize = 8.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

// 테스트 알림 팝업
@Composable
private fun TestNotificationPopup(
    notificationType: TestNotificationType,
    onDismiss: () -> Unit
) {
    val isHighTide = notificationType == TestNotificationType.HIGH_TIDE
    val title = if (isHighTide) "🌊 만조 30분 전!" else "🏖️ 간조 30분 전!"
    val timeRemaining = "30분"
    val height = if (isHighTide) "120cm" else "25cm"
    
    val advice = if (isHighTide) {
        "만조가 다가오고 있습니다!\n• 물고기들이 활발해집니다\n• 대물 낚시 기회입니다\n• 찌낚시나 원투낚시 추천\n• 안전에 주의하세요"
    } else {
        "간조가 다가오고 있습니다!\n• 바다낚시 최적의 시간\n• 갯벌체험 가능 시간\n• 초보자도 낚시하기 좋음\n• 조개나 게 채집 기회"
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(
                    Color.Black.copy(alpha = 0.95f),
                    RoundedCornerShape(16.dp)
                )
                .border(
                    2.dp,
                    if (isHighTide) Color(0xFF2196F3) else Color(0xFF4CAF50),
                    RoundedCornerShape(16.dp)
                )
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 제목
            Text(
                text = title,
                color = if (isHighTide) Color(0xFF2196F3) else Color(0xFF4CAF50),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(6.dp))
            
            // 시간 정보
            Text(
                text = "${timeRemaining} 후 ${if (isHighTide) "만조" else "간조"} ${height}",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(10.dp))
            
            // 조언 내용
            Text(
                text = advice,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 9.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                lineHeight = 12.sp
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 닫기 안내
            Text(
                text = "화면을 터치하여 닫기",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 7.sp
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TideScreenPreview() {
    TideScreen(onNavigateBack = {})
}