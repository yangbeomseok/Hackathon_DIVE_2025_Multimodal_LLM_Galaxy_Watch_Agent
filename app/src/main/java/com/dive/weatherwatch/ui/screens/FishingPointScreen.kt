package com.dive.weatherwatch.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Waves
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import com.dive.weatherwatch.data.FishingPoint
import com.dive.weatherwatch.data.FishingHotspot
import com.dive.weatherwatch.services.FishingHotspotNotificationService
import kotlin.math.atan2
import com.dive.weatherwatch.ui.components.DynamicBackgroundOverlay
import com.dive.weatherwatch.ui.theme.WearColors
import com.dive.weatherwatch.ui.theme.WearDimensions
import kotlin.math.*
import androidx.compose.ui.platform.LocalContext

// 거리 계산 함수
fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadius = 6371.0
    val dLat = (lat2 - lat1).toRadians()
    val dLon = (lon2 - lon1).toRadians()
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1.toRadians()) * cos(lat2.toRadians()) *
            sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadius * c
}

fun Double.toRadians() = this * PI / 180

// 어종별 아이콘 매핑
fun getFishIcon(target: String): String {
    return when {
        target.contains("학꽁치", ignoreCase = true) -> "🐟"
        target.contains("갈치", ignoreCase = true) -> "🐠" 
        target.contains("고등어", ignoreCase = true) -> "🐟"
        target.contains("전갱이", ignoreCase = true) -> "🐠"
        target.contains("방어", ignoreCase = true) -> "🐟"
        target.contains("감성돔", ignoreCase = true) -> "🐠"
        target.contains("농어", ignoreCase = true) -> "🐟"
        target.contains("광어", ignoreCase = true) -> "🐠"
        target.contains("우럭", ignoreCase = true) -> "🐟"
        target.contains("돔", ignoreCase = true) -> "🐠"
        target.contains("볼락", ignoreCase = true) -> "🐟"
        target.contains("문어", ignoreCase = true) -> "🐙"
        target.contains("쭈꾸미", ignoreCase = true) -> "🐙"
        target.contains("갑오징어", ignoreCase = true) -> "🦑"
        target.contains("오징어", ignoreCase = true) -> "🦑"
        else -> "🎣"
    }
}

// 현재 계절 계산
fun getCurrentSeason(): String {
    val month = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1
    return when (month) {
        3, 4, 5 -> "봄"
        6, 7, 8 -> "여름"
        9, 10, 11 -> "가을"
        else -> "겨울"
    }
}

// 고농도 엽록소 포인트 샘플 데이터 (부산 근해)
fun getHighConcentrationChlorophyllPoints(): List<FishingHotspot> {
    return listOf(
        FishingHotspot(35.1595, 129.1615, 4.25, "A"), // 부산 해운대 근해
        FishingHotspot(35.1012, 129.0310, 3.85, "A"), // 부산 영도구 근해  
        FishingHotspot(35.0456, 128.9876, 3.92, "A"), // 부산 서구 근해
        FishingHotspot(35.2134, 129.2456, 4.15, "A"), // 부산 기장군 근해
        FishingHotspot(35.0789, 129.1234, 3.75, "B"), // 부산 남구 근해
        FishingHotspot(35.1876, 129.0987, 3.68, "B"), // 부산 동래구 근해
    )
}

// 방위각 계산 (북쪽 기준)
fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLon = (lon2 - lon1).toRadians()
    val lat1Rad = lat1.toRadians()
    val lat2Rad = lat2.toRadians()
    
    val y = sin(dLon) * cos(lat2Rad)
    val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)
    
    val bearing = atan2(y, x) * 180.0 / PI
    return (bearing + 360.0) % 360.0
}

// 방위각을 방향으로 변환
fun bearingToDirection(bearing: Double): String {
    return when ((bearing / 45.0).toInt()) {
        0, 8 -> "북"
        1 -> "북동"
        2 -> "동"
        3 -> "남동"
        4 -> "남"
        5 -> "남서"
        6 -> "서"
        7 -> "북서"
        else -> "북"
    }
}

// 가장 가까운 고농도 엽록소 포인트 찾기
fun findNearestHighConcentrationPoint(userLat: Double, userLon: Double): FishingHotspot? {
    val points = getHighConcentrationChlorophyllPoints()
    return points.filter { it.grade == "A" && it.medianConcentration >= 3.5 } // 고농도만 필터링
        .minByOrNull { calculateDistance(userLat, userLon, it.latitude, it.longitude) }
}

// 좌표를 기반으로 대략적인 위치명 반환
fun getCurrentLocationName(lat: Double, lon: Double): String {
    return when {
        lat in 35.0..35.4 && lon in 128.8..129.3 -> "부산광역시"
        lat in 35.4..35.7 && lon in 129.0..129.5 -> "울산광역시"
        lat in 35.7..36.0 && lon in 128.3..128.8 -> "대구광역시"
        lat in 34.5..35.8 && lon in 127.5..129.5 -> "경상남도"
        lat in 35.5..37.5 && lon in 128.0..130.0 -> "경상북도"
        lat in 33.8..35.5 && lon in 125.0..127.8 -> "전라남도"
        lat in 35.0..36.3 && lon in 126.2..128.0 -> "전라북도"
        lat in 35.5..37.0 && lon in 125.5..127.5 -> "충청남도"
        lat in 36.0..37.5 && lon in 127.0..129.0 -> "충청북도"
        lat in 37.0..38.0 && lon in 126.5..127.8 -> when {
            lat > 37.4 && lon < 127.2 -> "서울특별시"
            else -> "경기도"
        }
        lat in 37.2..37.7 && lon in 126.0..126.8 -> "인천광역시"
        lat in 37.0..38.8 && lon in 127.5..129.5 -> "강원특별자치도"
        lat in 33.0..33.8 && lon in 126.0..127.0 -> "제주특별자치도"
        else -> "대한민국"
    }
}

// 대상 어종/채비 정보 포맷팅 함수
fun formatFishTargetInfo(target: String): String {
    if (target == "정보 없음" || target.isBlank()) return target
    return target
        .replace("▶", "\n• ")
        .replace("-", ": ")
        .trim()
        .let { if (!it.startsWith("•")) "• $it" else it }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun FishingPointScreen(
    fishingPoints: List<FishingPoint>,
    userLat: Double? = null,
    userLon: Double? = null,
    locationName: String? = null,
    onBackClick: () -> Unit,
    onNavigateToCompass: ((Double, Double, String) -> Unit)? = null
) {
    var selectedPoint by remember { mutableStateOf<FishingPoint?>(null) }
    
    Box(modifier = Modifier.fillMaxSize()) {
        DynamicBackgroundOverlay(
            weatherData = null,
            alpha = 0.7f,
            forceTimeBasedBackground = true
        )
        
        AnimatedContent(
            targetState = selectedPoint,
            transitionSpec = {
                if (targetState != null) {
                    slideInHorizontally { it } with slideOutHorizontally { -it }
                } else {
                    slideInHorizontally { -it } with slideOutHorizontally { it }
                }
            }
        ) { point ->
            if (point == null) {
                FishingPointListScreen(
                    fishingPoints = fishingPoints,
                    userLat = userLat,
                    userLon = userLon,
                    locationName = locationName,
                    onBackClick = onBackClick,
                    onPointClick = { selectedPoint = it },
                    onNavigateToCompass = onNavigateToCompass
                )
            } else {
                FishingPointDetailScreen(
                    point = point,
                    onBackClick = { selectedPoint = null }
                )
            }
        }
    }
}

@Composable
fun FishingPointListScreen(
    fishingPoints: List<FishingPoint>,
    userLat: Double?,
    userLon: Double?,
    locationName: String?,
    onBackClick: () -> Unit,
    onPointClick: (FishingPoint) -> Unit,
    onNavigateToCompass: ((Double, Double, String) -> Unit)? = null
) {
    var showAreaDetail by remember { mutableStateOf(false) }
    var showFishingIndexDialog by remember { mutableStateOf(false) }
    var showChlorophyllDialog by remember { mutableStateOf(false) }
    var nearestHotspot by remember { mutableStateOf<FishingHotspot?>(null) }
    
    // 화면 진입시 가장 가까운 고농도 엽록소 포인트 찾기
    LaunchedEffect(userLat, userLon) {
        if (userLat != null && userLon != null) {
            val hotspot = findNearestHighConcentrationPoint(userLat, userLon)
            if (hotspot != null) {
                nearestHotspot = hotspot
                showChlorophyllDialog = true
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 0.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "낚시 포인트",
                    style = MaterialTheme.typography.body2.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.width(4.dp))
                
                Box(
                    modifier = Modifier
                        .background(
                            color = WearColors.AccentYellow.copy(alpha = 0.4f),
                            shape = CircleShape
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "총 ${fishingPoints.size}곳",
                        style = MaterialTheme.typography.body2.copy(
                            fontSize = 6.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White
                    )
                }
            }
            
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (userLat != null && userLon != null) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "현재 위치",
                            modifier = Modifier.size(8.dp),
                            tint = Color(0xFF4CAF50)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "위치: ${String.format("%.4f", userLat)}, ${String.format("%.4f", userLon)}",
                            style = MaterialTheme.typography.body2.copy(
                                fontSize = 6.sp
                            ),
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    
                    val fallbackLocationName = getCurrentLocationName(userLat, userLon)
                    val displayLocationName = when {
                        !locationName.isNullOrBlank() -> locationName
                        else -> fallbackLocationName
                    }
                    
                    Text(
                        text = displayLocationName,
                        style = MaterialTheme.typography.body2.copy(
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color.White.copy(alpha = 0.9f)
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "위치 정보 없음",
                            modifier = Modifier.size(8.dp),
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "위치 정보를 가져오는 중...",
                            style = MaterialTheme.typography.body2.copy(
                                fontSize = 6.sp
                            ),
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(2.dp))
        
        val sortedPoints = remember(userLat, userLon, fishingPoints) {
            if (userLat != null && userLon != null) {
                fishingPoints.sortedBy { point ->
                    calculateDistance(userLat, userLon, point.lat, point.lon)
                }
            } else {
                fishingPoints
            }
        }
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 4.dp)
        ) {
            // 낚시 지수 카드 추가 (맨 위)
            item {
                FishingIndexCard(
                    onCardClick = { showFishingIndexDialog = true }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            
            itemsIndexed(sortedPoints) { index, point ->
                FishingPointCard(
                    point = point,
                    distance = if (userLat != null && userLon != null) {
                        calculateDistance(userLat, userLon, point.lat, point.lon)
                    } else null,
                    onClick = { onPointClick(point) },
                    index = index
                )
            }
            
            // 지역 정보 카드를 맨 밑으로 이동
            if (sortedPoints.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    if (showAreaDetail) {
                        AreaDetailCard(
                            point = sortedPoints.first(),
                            onBackClick = { showAreaDetail = false }
                        )
                    } else {
                        AreaSummaryCard(
                            point = sortedPoints.first(),
                            onClick = { showAreaDetail = true }
                        )
                    }
                }
            }
            
            // 엽록소 푸시 알림 테스트 버튼 추가 (맨 하단)
            item {
                Spacer(modifier = Modifier.height(8.dp))
                ChlorophyllNotificationTestButtons()
            }
        }
    }
    
    // 낚시 지수 알고리즘 설명 다이얼로그 (최상위 레벨)
    if (showFishingIndexDialog) {
        FishingIndexAlgorithmDialog(
            onDismiss = { showFishingIndexDialog = false }
        )
    }
    
    // 고농도 엽록소 포인트 안내 다이얼로그
    if (showChlorophyllDialog && nearestHotspot != null && userLat != null && userLon != null) {
        ChlorophyllHotspotDialog(
            hotspot = nearestHotspot!!,
            userLat = userLat,
            userLon = userLon,
            onDismiss = { showChlorophyllDialog = false },
            onNavigate = { 
                showChlorophyllDialog = false
                // 나침반 화면으로 이동하며 목표 지점 정보 전달
                onNavigateToCompass?.invoke(
                    nearestHotspot!!.latitude,
                    nearestHotspot!!.longitude,
                    getCurrentLocationName(nearestHotspot!!.latitude, nearestHotspot!!.longitude)
                )
            }
        )
    }
}

@Composable
fun FishingIndexAlgorithmDialog(
    onDismiss: () -> Unit,
    fishingIndexViewModel: com.dive.weatherwatch.ui.viewmodels.FishingIndexViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val algorithmExplanation by fishingIndexViewModel.algorithmExplanation.collectAsState()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            onClick = { }, // 빈 클릭 핸들러 추가
            modifier = Modifier
                .width(180.dp)
                .height(140.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            WearColors.AccentBlue.copy(alpha = 0.15f),
                            WearColors.AccentYellow.copy(alpha = 0.08f)
                        )
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                .border(
                    width = 1.dp,
                    color = WearColors.AccentBlue.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                WearColors.AccentBlue.copy(alpha = 0.05f),
                                Color.Transparent,
                                WearColors.AccentYellow.copy(alpha = 0.03f)
                            )
                        )
                    )
                    .padding(8.dp)
            ) {
                Column {
                    // 제목
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📊 계산 상세",
                            style = MaterialTheme.typography.body1.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = WearColors.TextPrimary
                        )
                        
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(24.dp)
                                .semantics {
                                    contentDescription = "다이얼로그 닫기"
                                    role = Role.Button
                                }
                        ) {
                            Text(
                                text = "×",
                                fontSize = 12.sp,
                                color = MaterialTheme.colors.onPrimary
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    // 내용
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = if (algorithmExplanation.isNotBlank()) {
                                algorithmExplanation.replace("🎣 낚시지수 계산 알고리즘", "").trim()
                            } else {
                                "📊 실시간 데이터 기반 계산\n\n" +
                                "🌤️ 날씨 점수 (가중치 25%)\n" +
                                "• 맑음: 5점 | 구름: 4점 | 흐림: 3점 | 비: 1점\n\n" +
                                "🌊 물때 점수 (가중치 25%)\n" +
                                "• 조차 150cm↑: 5점 (대조)\n" +
                                "• 조차 100-150cm: 4점 (중조)\n" +
                                "• 조차 50-100cm: 3점 (소조)\n" +
                                "• 조차 50cm↓: 2점\n\n" +
                                "🌡️ 수온 점수 (가중치 20%)\n" +
                                "• 18-25°C: 5점 (최적)\n" +
                                "• 15-18°C, 25-28°C: 3점\n" +
                                "• 기타: 1점\n\n" +
                                "💨 파고/풍속 점수 (가중치 15%)\n" +
                                "• 파고 0.5m↓, 풍속 3m/s↓: 5점\n" +
                                "• 파고 1m↓, 풍속 5m/s↓: 3점\n\n" +
                                "☁️ 구름량 점수 (가중치 15%)\n" +
                                "• 맑음: 5점 | 구름많음: 3점 | 흐림: 2점\n\n" +
                                "🎯 최종 계산\n" +
                                "각 점수 × 가중치 합산 → 1-5점 변환"
                            },
                            style = MaterialTheme.typography.body2.copy(
                                fontSize = 6.sp,
                                lineHeight = 8.sp
                            ),
                            color = WearColors.TextSecondary,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AreaSummaryCard(point: FishingPoint, onClick: () -> Unit = {}) {
    // 디버깅을 위한 로그 출력
    println("AreaSummaryCard - point.name: '${point.name}'")
    println("AreaSummaryCard - forecast: '${point.forecast}'")
    println("AreaSummaryCard - notice: '${point.notice}'")
    println("AreaSummaryCard - fishSu: '${point.fishSu}'")
    println("AreaSummaryCard - wtempSu: '${point.wtempSu}'")
    
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        WearColors.AccentBlue.copy(alpha = 0.15f),
                        WearColors.AccentYellow.copy(alpha = 0.08f)
                    )
                ),
                shape = RoundedCornerShape(WearDimensions.CardCornerRadius)
            )
            .border(
                width = 1.dp,
                color = WearColors.AccentBlue.copy(alpha = 0.3f),
                shape = RoundedCornerShape(WearDimensions.CardCornerRadius)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            WearColors.AccentBlue.copy(alpha = 0.05f),
                            Color.Transparent,
                            WearColors.AccentYellow.copy(alpha = 0.03f)
                        )
                    )
                )
                .padding(6.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "🌊", fontSize = 10.sp)
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "지역 정보",
                        style = MaterialTheme.typography.body2.copy(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = WearColors.TextPrimary
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                val currentSeason = getCurrentSeason()
                println("AreaSummaryCard - currentSeason: $currentSeason")
                val currentFish = when (currentSeason) {
                    "봄" -> point.fishSp
                    "여름" -> point.fishSu
                    "가을" -> point.fishFa
                    "겨울" -> point.fishWi
                    else -> ""
                }
                println("AreaSummaryCard - currentFish: $currentFish")
                
                if (currentFish.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(
                                    color = WearColors.AccentYellow.copy(alpha = 0.2f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "🐟", fontSize = 7.sp)
                        }
                        
                        Spacer(modifier = Modifier.width(3.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${currentSeason} 주요 어종",
                                style = MaterialTheme.typography.body2.copy(
                                    fontSize = 5.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = WearColors.TextSecondary
                            )
                            Text(
                                text = currentFish,
                                style = MaterialTheme.typography.body2.copy(fontSize = 6.sp),
                                color = WearColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                }
                
                val currentTemp = when (currentSeason) {
                    "봄" -> point.wtempSp
                    "여름" -> point.wtempSu
                    "가을" -> point.wtempFa
                    "겨울" -> point.wtempWi
                    else -> ""
                }
                println("AreaSummaryCard - currentTemp: $currentTemp")
                
                if (currentTemp.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(
                                    color = WearColors.AccentBlue.copy(alpha = 0.2f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "🌡️", fontSize = 7.sp)
                        }
                        
                        Spacer(modifier = Modifier.width(3.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${currentSeason} 수온",
                                style = MaterialTheme.typography.body2.copy(
                                    fontSize = 5.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = WearColors.TextSecondary
                            )
                            Text(
                                text = currentTemp,
                                style = MaterialTheme.typography.body2.copy(fontSize = 6.sp),
                                color = WearColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                
                // 해상 예보
                Spacer(modifier = Modifier.height(3.dp))
                
                val forecastText = point.forecast
                    .replace("\\n", " ")
                    .replace("\n", " ")
                    .trim()
                
                val forecastPreview = if (forecastText.isBlank()) {
                    "해상 예보 정보가 없습니다."
                } else if (forecastText.length > 50) {
                    "${forecastText.take(50)}..."
                } else {
                    forecastText
                }
                
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(
                                color = WearColors.AccentYellow.copy(alpha = 0.15f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🌤️", fontSize = 7.sp)
                    }
                    
                    Spacer(modifier = Modifier.width(3.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "해상 예보",
                            style = MaterialTheme.typography.body2.copy(
                                fontSize = 5.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = WearColors.TextSecondary
                        )
                        Text(
                            text = forecastPreview,
                            style = MaterialTheme.typography.body2.copy(
                                fontSize = 6.sp,
                                lineHeight = 7.sp
                            ),
                            color = if (forecastText.isBlank()) WearColors.TextSecondary.copy(alpha = 0.7f) else WearColors.TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                // 주의사항
                Spacer(modifier = Modifier.height(3.dp))
                
                val noticeText = point.notice
                    .replace("\\n", " ")
                    .replace("\n", " ")
                    .trim()
                
                val noticePreview = if (noticeText.isBlank()) {
                    "주의사항 정보가 없습니다."
                } else if (noticeText.length > 50) {
                    "${noticeText.take(50)}..."
                } else {
                    noticeText
                }
                
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(
                                color = Color.Red.copy(alpha = 0.15f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "⚠️", fontSize = 7.sp)
                    }
                    
                    Spacer(modifier = Modifier.width(3.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "주의사항",
                            style = MaterialTheme.typography.body2.copy(
                                fontSize = 5.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = WearColors.TextSecondary
                        )
                        Text(
                            text = noticePreview,
                            style = MaterialTheme.typography.body2.copy(
                                fontSize = 6.sp,
                                lineHeight = 7.sp
                            ),
                            color = if (noticeText.isBlank()) WearColors.TextSecondary.copy(alpha = 0.7f) else WearColors.TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FishingIndexCard(
    onCardClick: () -> Unit = {},
    fishingIndexViewModel: com.dive.weatherwatch.ui.viewmodels.FishingIndexViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    weatherViewModel: com.dive.weatherwatch.ui.viewmodels.WeatherViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    tideViewModel: com.dive.weatherwatch.ui.viewmodels.TideViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val fishingIndexData by fishingIndexViewModel.fishingIndexData.collectAsState()
    val dynamicFishingIndex by fishingIndexViewModel.dynamicFishingIndex.collectAsState()
    val algorithmExplanation by fishingIndexViewModel.algorithmExplanation.collectAsState()
    val isLoading by fishingIndexViewModel.isLoading.collectAsState()
    val error by fishingIndexViewModel.error.collectAsState()
    
    val weatherData by weatherViewModel.weatherData.collectAsState()
    val tideData by tideViewModel.weeklyTideInfo.collectAsState()
    
    // showDialog 제거 - 상위 컴포넌트에서 관리
    
    // API 호출
    LaunchedEffect(Unit) {
        fishingIndexViewModel.loadFishingIndex("PL6rJjVkoo/1g6B41uLFacfcit691ahg3nnJf5Ot7hJ2QlIau3oHKQub8sRNHCfp8mYIVbmYl8VBFV5s0/d2ow==")
    }
    
    // 동적 낚시 지수 계산
    LaunchedEffect(weatherData, tideData) {
        if (weatherData != null || tideData.isNotEmpty()) {
            fishingIndexViewModel.calculateDynamicFishingIndex(
                weatherData = weatherData,
                tideData = tideData,
                targetFish = "감성돔"
            )
        }
    }
    
    // 첫 번째 데이터 항목 사용
    val currentData = fishingIndexData.firstOrNull()
    Card(
        onClick = onCardClick,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        WearColors.AccentYellow.copy(alpha = 0.15f),
                        WearColors.AccentBlue.copy(alpha = 0.08f)
                    )
                ),
                shape = RoundedCornerShape(WearDimensions.CardCornerRadius)
            )
            .border(
                width = 1.dp,
                color = WearColors.AccentYellow.copy(alpha = 0.3f),
                shape = RoundedCornerShape(WearDimensions.CardCornerRadius)
            )
            .semantics {
                contentDescription = "낚시 지수 카드. 터치하면 계산 알고리즘을 확인할 수 있습니다."
                role = Role.Button
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            WearColors.AccentYellow.copy(alpha = 0.05f),
                            Color.Transparent,
                            WearColors.AccentBlue.copy(alpha = 0.03f)
                        )
                    )
                )
                .padding(6.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "🎣", fontSize = 10.sp)
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "낚시 지수",
                        style = MaterialTheme.typography.body2.copy(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = WearColors.TextPrimary
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // 대상어 정보
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(
                                color = WearColors.AccentYellow.copy(alpha = 0.2f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🐟", fontSize = 7.sp)
                    }
                    
                    Spacer(modifier = Modifier.width(3.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "대상어",
                            style = MaterialTheme.typography.body2.copy(
                                fontSize = 5.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = WearColors.TextSecondary
                        )
                        Text(
                            text = when {
                                isLoading -> "로딩 중..."
                                error != null -> "오류: ${error}"
                                currentData != null -> currentData.targetFish
                                fishingIndexData.isEmpty() -> "데이터 없음 (샘플)"
                                else -> "정보 없음"
                            },
                            style = MaterialTheme.typography.body2.copy(fontSize = 6.sp),
                            color = if (error != null) Color.Red else WearColors.TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(3.dp))
                
                // 낚시 지수
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(
                                color = WearColors.AccentBlue.copy(alpha = 0.2f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "📊", fontSize = 7.sp)
                    }
                    
                    Spacer(modifier = Modifier.width(3.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "낚시 지수",
                            style = MaterialTheme.typography.body2.copy(
                                fontSize = 5.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = WearColors.TextSecondary
                        )
                        Text(
                            text = when {
                                isLoading -> "로딩 중..."
                                error != null -> "오류"
                                dynamicFishingIndex > 0 -> {
                                    val index = dynamicFishingIndex
                                    when (index) {
                                        1 -> "1점 (매우 나쁨) 🔴"
                                        2 -> "2점 (나쁨) 🟠"
                                        3 -> "3점 (보통) 🟡"
                                        4 -> "4점 (좋음) 🟢"
                                        5 -> "5점 (매우 좋음) 🔵"
                                        else -> "${index}점"
                                    }
                                }
                                currentData != null -> {
                                    val index = currentData.fishingIndex
                                    when (index) {
                                        1 -> "1점 (매우 나쁨)"
                                        2 -> "2점 (나쁨)"
                                        3 -> "3점 (보통)"
                                        4 -> "4점 (좋음)"
                                        5 -> "5점 (매우 좋음)"
                                        else -> "${index}점"
                                    }
                                }
                                fishingIndexData.isEmpty() -> "샘플 데이터"
                                else -> "정보 없음"
                            },
                            style = MaterialTheme.typography.body2.copy(fontSize = 6.sp),
                            color = WearColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(3.dp))
                
                // 낚시 점수
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(
                                color = WearColors.AccentYellow.copy(alpha = 0.2f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "⭐", fontSize = 7.sp)
                    }
                    
                    Spacer(modifier = Modifier.width(3.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "낚시 점수",
                            style = MaterialTheme.typography.body2.copy(
                                fontSize = 5.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = WearColors.TextSecondary
                        )
                        Text(
                            text = when {
                                isLoading -> "로딩 중..."
                                error != null -> "오류"
                                dynamicFishingIndex > 0 -> "${dynamicFishingIndex * 20}점 (실시간)"
                                currentData != null -> "${currentData.fishingScore}점"
                                fishingIndexData.isEmpty() -> "샘플"
                                else -> "0점"
                            },
                            style = MaterialTheme.typography.body2.copy(fontSize = 6.sp),
                            color = WearColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AreaDetailCard(point: FishingPoint, onBackClick: () -> Unit) {
    Card(
        onClick = { },
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        WearColors.AccentBlue.copy(alpha = 0.15f),
                        WearColors.AccentYellow.copy(alpha = 0.08f)
                    )
                ),
                shape = RoundedCornerShape(WearDimensions.CardCornerRadius)
            )
            .border(
                width = 1.dp,
                color = WearColors.AccentBlue.copy(alpha = 0.3f),
                shape = RoundedCornerShape(WearDimensions.CardCornerRadius)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            WearColors.AccentBlue.copy(alpha = 0.05f),
                            Color.Transparent,
                            WearColors.AccentYellow.copy(alpha = 0.03f)
                        )
                    )
                )
                .padding(6.dp)
        ) {
            Column {
                // 헤더 - 뒤로가기 버튼과 제목
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onBackClick,
                        modifier = Modifier.size(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "뒤로가기",
                            modifier = Modifier.size(8.dp),
                            tint = MaterialTheme.colors.onPrimary
                        )
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "🌊", fontSize = 10.sp)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "지역 상세 정보",
                            style = MaterialTheme.typography.body2.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = WearColors.TextPrimary
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                // 지역 소개
                if (point.intro.isNotBlank()) {
                    DetailInfoSection(
                        icon = "📍",
                        title = "지역 소개",
                        content = point.intro
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                
                // 해상 예보
                if (point.forecast.isNotBlank()) {
                    DetailInfoSection(
                        icon = "🌤️",
                        title = "해상 예보",
                        content = point.forecast
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                
                // 조류 정보
                if (point.ebbf.isNotBlank()) {
                    DetailInfoSection(
                        icon = "🌊",
                        title = "조류 정보",
                        content = point.ebbf
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                
                // 주의사항
                if (point.notice.isNotBlank()) {
                    DetailInfoSection(
                        icon = "⚠️",
                        title = "주의사항",
                        content = point.notice
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                
                // 계절별 정보
                DetailSeasonalSection(point = point)
            }
        }
    }
}

@Composable
fun DetailInfoSection(icon: String, title: String, content: String) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = icon, fontSize = 8.sp)
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.body2.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = WearColors.TextPrimary
            )
        }
        
        Spacer(modifier = Modifier.height(2.dp))
        
        Text(
            text = content,
            style = MaterialTheme.typography.body2.copy(
                fontSize = 9.sp,
                lineHeight = 12.sp
            ),
            color = WearColors.TextSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 11.dp)
        )
    }
}

@Composable
fun DetailSeasonalSection(point: FishingPoint) {
    val seasons = listOf(
        "봄" to (point.wtempSp to point.fishSp),
        "여름" to (point.wtempSu to point.fishSu),
        "가을" to (point.wtempFa to point.fishFa),
        "겨울" to (point.wtempWi to point.fishWi)
    )
    
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "🌿", fontSize = 8.sp)
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "계절별 정보",
                style = MaterialTheme.typography.body2.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = WearColors.TextPrimary
            )
        }
        
        Spacer(modifier = Modifier.height(3.dp))
        
        seasons.forEach { (season, data) ->
            val (temp, fish) = data
            if (temp.isNotBlank() || fish.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 11.dp, bottom = 3.dp)
                ) {
                    Text(
                        text = "• $season",
                        style = MaterialTheme.typography.body2.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = WearColors.TextPrimary
                    )
                    
                    if (temp.isNotBlank()) {
                        Text(
                            text = "  수온: $temp",
                            style = MaterialTheme.typography.body2.copy(fontSize = 8.sp),
                            color = WearColors.TextSecondary
                        )
                    }
                    
                    if (fish.isNotBlank()) {
                        Text(
                            text = "  어종: $fish",
                            style = MaterialTheme.typography.body2.copy(fontSize = 8.sp),
                            color = WearColors.TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FishingPointCard(
    point: FishingPoint,
    distance: Double?,
    onClick: () -> Unit,
    index: Int
) {
    val animatedAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = 400,
            delayMillis = index * 80,
            easing = FastOutSlowInEasing
        )
    )
    
    val animatedOffset by animateIntAsState(
        targetValue = 0,
        animationSpec = tween(
            durationMillis = 500,
            delayMillis = index * 80,
            easing = FastOutSlowInEasing
        )
    )
    
    val animatedScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
            visibilityThreshold = 0.01f
        )
    )
    
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(animatedAlpha)
            .offset(y = animatedOffset.dp)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .background(
                color = WearColors.CardBackground.copy(alpha = 0.08f),
                shape = RoundedCornerShape(8.dp)
            )
            .semantics {
                contentDescription = "${point.name} 낚시 포인트. ${distance?.let { "거리 ${String.format("%.1f", it)}km" } ?: ""}터치하면 자세한 정보를 확인할 수 있습니다."
                role = Role.Button
            }
    ) {
        Row(
            modifier = Modifier
                .padding(4.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(
                        color = WearColors.AccentBlue.copy(alpha = 0.15f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = getFishIcon(point.target), fontSize = 10.sp)
            }
            
            Spacer(modifier = Modifier.width(4.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = point.pointNm.ifBlank { point.name },
                    style = MaterialTheme.typography.body2.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 8.sp
                    ),
                    color = WearColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(1.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (distance != null) {
                        Text(
                            text = "${String.format("%.1f", distance)}km",
                            style = MaterialTheme.typography.body2.copy(
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = WearColors.AccentYellow
                        )
                        
                        Text(
                            text = " • ",
                            style = MaterialTheme.typography.body2.copy(fontSize = 7.sp),
                            color = WearColors.TextTertiary
                        )
                    }
                    
                    Text(
                        text = point.target.split(",", ":").firstOrNull()?.trim() ?: "낚시",
                        style = MaterialTheme.typography.body2.copy(fontSize = 7.sp),
                        color = WearColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(10.dp),
                tint = WearColors.AccentBlue.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun FishingPointDetailScreen(
    point: FishingPoint,
    onBackClick: () -> Unit
) {
    var selectedSeason by remember { mutableStateOf(getCurrentSeason()) }
    var expandedSection by remember { mutableStateOf<String?>(null) }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(6.dp)
    ) {
        item {
            Card(
                onClick = onBackClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                WearColors.CardBackground.copy(alpha = 0.2f),
                                WearColors.CardBackground.copy(alpha = 0.15f)
                            )
                        ),
                        shape = RoundedCornerShape(WearDimensions.CardCornerRadius)
                    )
                    .border(
                        width = 1.dp,
                        color = WearColors.AccentBlue.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(WearDimensions.CardCornerRadius)
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    WearColors.AccentBlue.copy(alpha = 0.05f),
                                    Color.Transparent,
                                    WearColors.AccentYellow.copy(alpha = 0.02f)
                                )
                            )
                        )
                        .padding(3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = point.pointNm.ifBlank { point.name },
                            style = MaterialTheme.typography.body2.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = WearColors.TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        
                        if (point.addr.isNotBlank()) {
                            Spacer(modifier = Modifier.height(1.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(8.dp),
                                    tint = WearColors.AccentYellow
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = point.addr,
                                    style = MaterialTheme.typography.body2.copy(fontSize = 8.sp),
                                    color = WearColors.TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
        
        item { Spacer(modifier = Modifier.height(6.dp)) }
        
        item {
            OverviewSection(point = point)
        }
        
        item { Spacer(modifier = Modifier.height(6.dp)) }
        
        item {
            FishingPointPhotoSection(photoUrl = point.photo)
        }
        
        item { Spacer(modifier = Modifier.height(6.dp)) }
        
        item {
            SeasonalInfoSection(
                point = point,
                selectedSeason = selectedSeason,
                onSeasonSelected = { selectedSeason = it }
            )
        }
        
        item { Spacer(modifier = Modifier.height(6.dp)) }
        
        item {
            AreaInfoSection(
                point = point,
                expandedSection = expandedSection,
                onSectionToggle = { section ->
                    expandedSection = if (expandedSection == section) null else section
                }
            )
        }
        
        item { Spacer(modifier = Modifier.height(6.dp)) }
    }
}

fun getFullPhotoUrl(photo: String): String {
    return if (photo.isNotBlank() && !photo.startsWith("http")) {
        "https://www.badatime.com/img/point_img/thumbnail/$photo"
    } else {
        photo
    }
}

@Composable
fun FishingPointPhotoSection(photoUrl: String) {
    Card(
        onClick = { },
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        WearColors.CardBackground.copy(alpha = 0.15f),
                        WearColors.CardBackground.copy(alpha = 0.1f)
                    )
                ),
                shape = RoundedCornerShape(WearDimensions.CardCornerRadius)
            )
            .border(
                width = 0.5.dp,
                color = WearColors.DividerColor.copy(alpha = 0.3f),
                shape = RoundedCornerShape(WearDimensions.CardCornerRadius)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            WearColors.CardBackground.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    )
                )
                .padding(6.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "📸", fontSize = 10.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "포인트 사진",
                        style = MaterialTheme.typography.body2.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = WearColors.TextPrimary
                    )
                }
            
                Spacer(modifier = Modifier.height(4.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(
                            color = WearColors.CardBackground.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = WearColors.DividerColor.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (photoUrl.isNotBlank()) {
                        AsyncImage(
                            model = getFullPhotoUrl(photoUrl),
                            contentDescription = "낚시 포인트 사진",
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.Transparent
                                ),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "📷",
                                fontSize = 16.sp,
                                color = WearColors.TextSecondary.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "사진 없음",
                                style = MaterialTheme.typography.body2.copy(
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = WearColors.TextSecondary.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OverviewSection(point: FishingPoint) {
    Card(
        onClick = { },
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        WearColors.CardBackground.copy(alpha = 0.15f),
                        WearColors.CardBackground.copy(alpha = 0.1f)
                    )
                ),
                shape = RoundedCornerShape(WearDimensions.CardCornerRadius)
            )
            .border(
                width = 0.5.dp,
                color = WearColors.DividerColor.copy(alpha = 0.3f),
                shape = RoundedCornerShape(WearDimensions.CardCornerRadius)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            WearColors.CardBackground.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    )
                )
                .padding(6.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Waves,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = WearColors.AccentBlue
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "주요 정보",
                        style = MaterialTheme.typography.body2.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = WearColors.TextPrimary
                    )
                }
            
                Spacer(modifier = Modifier.height(4.dp))
                
                InfoRow(
                    icon = "💧",
                    label = "수심",
                    value = point.dpwt.ifBlank { "정보 없음" }
                )
                
                Spacer(modifier = Modifier.height(3.dp))
                
                InfoRow(
                    icon = "🌊",
                    label = "적정 물때",
                    value = point.tideTime.ifBlank { "정보 없음" }
                )
                
                Spacer(modifier = Modifier.height(3.dp))
                
                InfoRow(
                    icon = "🪨",
                    label = "저질",
                    value = point.material.ifBlank { "정보 없음" }
                )
                
                Spacer(modifier = Modifier.height(3.dp))
                
                FishTargetInfoRow(
                    icon = "🎣",
                    label = "대상 어종/채비",
                    value = point.target.ifBlank { "정보 없음" }
                )
            }
        }
    }
}

@Composable
fun InfoRow(
    icon: String,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(
                    color = WearColors.AccentBlue.copy(alpha = 0.15f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(text = icon, fontSize = 8.sp)
        }
        
        Spacer(modifier = Modifier.width(4.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.body2.copy(
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = WearColors.TextSecondary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.body2.copy(fontSize = 8.sp),
                color = WearColors.TextPrimary
            )
        }
    }
}

@Composable
fun FishTargetInfoRow(
    icon: String,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(
                    color = WearColors.AccentBlue.copy(alpha = 0.15f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(text = icon, fontSize = 8.sp)
        }
        
        Spacer(modifier = Modifier.width(4.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.body2.copy(
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = WearColors.TextSecondary
            )
            
            val formattedValue = formatFishTargetInfo(value)
            Text(
                text = formattedValue,
                style = MaterialTheme.typography.body2.copy(
                    fontSize = 8.sp,
                    lineHeight = 10.sp
                ),
                color = WearColors.TextPrimary
            )
        }
    }
}

@Composable
fun SeasonalInfoSection(
    point: FishingPoint,
    selectedSeason: String,
    onSeasonSelected: (String) -> Unit
) {
    val seasons = listOf("봄", "여름", "가을", "겨울")
    
    Card(
        onClick = { },
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        WearColors.CardBackground.copy(alpha = 0.15f),
                        WearColors.CardBackground.copy(alpha = 0.1f)
                    )
                ),
                shape = RoundedCornerShape(WearDimensions.CardCornerRadius)
            )
            .border(
                width = 0.5.dp,
                color = WearColors.DividerColor.copy(alpha = 0.3f),
                shape = RoundedCornerShape(WearDimensions.CardCornerRadius)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            WearColors.AccentYellow.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    )
                )
                .padding(6.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🌿", fontSize = 10.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "계절별 정보",
                        style = MaterialTheme.typography.body2.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = WearColors.TextPrimary
                    )
                }
            
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    seasons.forEach { season ->
                        val isSelected = season == selectedSeason
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 3.dp)
                                .background(
                                    brush = if (isSelected) {
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                WearColors.AccentBlue.copy(alpha = 0.3f),
                                                WearColors.AccentYellow.copy(alpha = 0.2f)
                                            )
                                        )
                                    } else {
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.Transparent
                                            )
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    brush = if (isSelected) {
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                WearColors.AccentBlue,
                                                WearColors.AccentYellow
                                            )
                                        )
                                    } else {
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                WearColors.DividerColor.copy(alpha = 0.4f),
                                                WearColors.DividerColor.copy(alpha = 0.4f)
                                            )
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { onSeasonSelected(season) }
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = season,
                                style = MaterialTheme.typography.body2.copy(
                                    fontSize = 7.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isSelected) WearColors.TextPrimary else WearColors.TextSecondary
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                SeasonalContent(point = point, season = selectedSeason)
            }
        }
    }
}

@Composable
fun SeasonalContent(
    point: FishingPoint,
    season: String
) {
    val (temperature, fish) = when (season) {
        "봄" -> point.wtempSp to point.fishSp
        "여름" -> point.wtempSu to point.fishSu
        "가을" -> point.wtempFa to point.fishFa
        "겨울" -> point.wtempWi to point.fishWi
        else -> "" to ""
    }
    
    Column {
        if (temperature.isNotBlank()) {
            InfoRow(
                icon = "🌡️",
                label = "평균 수온",
                value = temperature
            )
        }
        
        if (fish.isNotBlank()) {
            if (temperature.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
            }
            InfoRow(
                icon = "🐟",
                label = "계절별 주요 어종",
                value = fish
            )
        }
        
        if (temperature.isBlank() && fish.isBlank()) {
            Text(
                text = "해당 계절 정보가 없습니다.",
                style = MaterialTheme.typography.body2.copy(fontSize = 7.sp),
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun AreaInfoSection(
    point: FishingPoint,
    expandedSection: String?,
    onSectionToggle: (String) -> Unit
) {
    val sections = listOf(
        "지역 소개" to point.intro,
        "해상 예보" to point.forecast,
        "조류" to point.ebbf,
        "주의사항" to point.notice
    )
    
    Card(
        onClick = { },
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        WearColors.CardBackground.copy(alpha = 0.15f),
                        WearColors.CardBackground.copy(alpha = 0.1f)
                    )
                ),
                shape = RoundedCornerShape(WearDimensions.CardCornerRadius)
            )
            .border(
                width = 0.5.dp,
                color = WearColors.DividerColor.copy(alpha = 0.3f),
                shape = RoundedCornerShape(WearDimensions.CardCornerRadius)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            WearColors.CardBackground.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    )
                )
                .padding(6.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🗺️", fontSize = 10.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "지역 정보",
                        style = MaterialTheme.typography.body2.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = WearColors.TextPrimary
                    )
                }
            
                Spacer(modifier = Modifier.height(4.dp))
                
                if (point.intro.isNotBlank()) {
                    Text(
                        text = point.intro,
                        style = MaterialTheme.typography.body2.copy(
                            fontSize = 8.sp,
                            lineHeight = 10.sp
                        ),
                        color = WearColors.TextSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                }
                
                sections.drop(1).forEach { (title, content) ->
                    if (content.isNotBlank()) {
                        ExpandableInfoSection(
                            title = title,
                            content = content,
                            isExpanded = expandedSection == title,
                            onToggle = { onSectionToggle(title) }
                        )
                        
                        Spacer(modifier = Modifier.height(3.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ExpandableInfoSection(
    title: String,
    content: String,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = if (isExpanded) {
                        Brush.horizontalGradient(
                            colors = listOf(
                                WearColors.AccentBlue.copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        )
                    } else {
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent
                            )
                        )
                    },
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable { onToggle() }
                .padding(vertical = 8.dp, horizontal = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.body2.copy(
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = if (isExpanded) WearColors.TextPrimary else WearColors.TextSecondary
                )
                
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    WearColors.AccentBlue.copy(alpha = if (isExpanded) 0.3f else 0.1f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(8.dp),
                        tint = WearColors.AccentBlue.copy(alpha = 0.8f)
                    )
                }
            }
        }
        
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                WearColors.CardBackground.copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
            ) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.body2.copy(
                        fontSize = 7.sp,
                        lineHeight = 9.sp
                    ),
                    color = WearColors.TextSecondary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun ChlorophyllNotificationTestButtons() {
    val context = LocalContext.current
    
    Card(
        onClick = { },
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E88E5).copy(alpha = 0.15f),
                        Color(0xFF00BCD4).copy(alpha = 0.08f)
                    )
                ),
                shape = RoundedCornerShape(WearDimensions.CardCornerRadius)
            )
            .border(
                width = 1.dp,
                color = Color(0xFF1E88E5).copy(alpha = 0.3f),
                shape = RoundedCornerShape(WearDimensions.CardCornerRadius)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1E88E5).copy(alpha = 0.05f),
                            Color.Transparent,
                            Color(0xFF00BCD4).copy(alpha = 0.03f)
                        )
                    )
                )
                .padding(8.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "🛰️", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "엽록소 알림 테스트",
                        style = MaterialTheme.typography.body2.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = WearColors.TextPrimary
                    )
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                // A등급 테스트 버튼
                Button(
                    onClick = {
                        val gradeASpot = FishingHotspot(
                            latitude = 35.1595,
                            longitude = 129.1615,
                            medianConcentration = 4.25,
                            grade = "A"
                        )
                        FishingHotspotNotificationService.showFishingSpotNotification(
                            context, gradeASpot, 150.0
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF4CAF50).copy(alpha = 0.2f)
                    )
                ) {
                    Text(
                        text = "A등급 (고농도 4.25)",
                        style = MaterialTheme.typography.body2.copy(fontSize = 7.sp),
                        color = Color(0xFF4CAF50)
                    )
                }
                
                Spacer(modifier = Modifier.height(3.dp))
                
                // B등급 테스트 버튼
                Button(
                    onClick = {
                        val gradeBSpot = FishingHotspot(
                            latitude = 35.1234,
                            longitude = 129.0987,
                            medianConcentration = 3.15,
                            grade = "B"
                        )
                        FishingHotspotNotificationService.showFishingSpotNotification(
                            context, gradeBSpot, 280.0
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFFFF9800).copy(alpha = 0.2f)
                    )
                ) {
                    Text(
                        text = "B등급 (중농도 3.15)",
                        style = MaterialTheme.typography.body2.copy(fontSize = 7.sp),
                        color = Color(0xFFFF9800)
                    )
                }
                
                Spacer(modifier = Modifier.height(3.dp))
                
                // C등급 테스트 버튼
                Button(
                    onClick = {
                        val gradeCSpot = FishingHotspot(
                            latitude = 35.0876,
                            longitude = 129.0234,
                            medianConcentration = 1.85,
                            grade = "C"
                        )
                        FishingHotspotNotificationService.showFishingSpotNotification(
                            context, gradeCSpot, 450.0
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF2196F3).copy(alpha = 0.2f)
                    )
                ) {
                    Text(
                        text = "C등급 (저농도 1.85)",
                        style = MaterialTheme.typography.body2.copy(fontSize = 7.sp),
                        color = Color(0xFF2196F3)
                    )
                }
            }
        }
    }
}

@Composable
fun ChlorophyllHotspotDialog(
    hotspot: FishingHotspot,
    userLat: Double,
    userLon: Double,
    onDismiss: () -> Unit,
    onNavigate: () -> Unit
) {
    val distance = calculateDistance(userLat, userLon, hotspot.latitude, hotspot.longitude)
    val bearing = calculateBearing(userLat, userLon, hotspot.latitude, hotspot.longitude)
    val direction = bearingToDirection(bearing)
    val locationCoords = "${String.format("%.4f", hotspot.latitude)}, ${String.format("%.4f", hotspot.longitude)}"
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            onClick = { }, // 빈 클릭 핸들러 추가
            modifier = Modifier
                .width(200.dp)
                .height(160.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF4CAF50).copy(alpha = 0.15f),
                            Color(0xFF2196F3).copy(alpha = 0.08f)
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .border(
                    width = 2.dp,
                    color = Color(0xFF4CAF50).copy(alpha = 0.6f),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF4CAF50).copy(alpha = 0.05f),
                                Color.Transparent,
                                Color(0xFF2196F3).copy(alpha = 0.03f)
                            )
                        )
                    )
                    .padding(12.dp)
            ) {
                Column {
                    // 제목
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🌊", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "고농도 엽록소",
                                style = MaterialTheme.typography.body1.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color(0xFF4CAF50)
                            )
                        }
                        
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(20.dp)
                                .semantics {
                                    contentDescription = "닫기"
                                    role = Role.Button
                                }
                        ) {
                            Text(
                                text = "×",
                                fontSize = 10.sp,
                                color = MaterialTheme.colors.onPrimary
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 정보 내용
                    Column(modifier = Modifier.weight(1f)) {
                        // 농도 정보
                        InfoRowSmall(
                            icon = "📊",
                            label = "농도",
                            value = "${String.format("%.2f", hotspot.medianConcentration)} mg/m³ (${hotspot.grade}급)",
                            valueColor = Color(0xFF4CAF50)
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // 위치 정보  
                        InfoRowSmall(
                            icon = "📍",
                            label = "위치",
                            value = locationCoords,
                            valueColor = Color.White
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // 거리 정보
                        InfoRowSmall(
                            icon = "🗺️",
                            label = "거리",
                            value = "${String.format("%.1f", distance)}km",
                            valueColor = Color(0xFFFFD700)
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // 방향 정보
                        InfoRowSmall(
                            icon = "🧭",
                            label = "방향",
                            value = "$direction (${String.format("%.0f", bearing)}°)",
                            valueColor = Color(0xFF2196F3)
                        )
                    }
                    
                    // 네비게이션 버튼
                    Button(
                        onClick = onNavigate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF4CAF50).copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(text = "🧭", fontSize = 10.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "나침반 길찾기",
                                style = MaterialTheme.typography.body2.copy(fontSize = 9.sp),
                                color = Color(0xFF4CAF50)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRowSmall(
    icon: String,
    label: String,
    value: String,
    valueColor: Color = WearColors.TextPrimary
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = icon, fontSize = 8.sp)
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$label:",
            style = MaterialTheme.typography.body2.copy(
                fontSize = 7.sp,
                fontWeight = FontWeight.Medium
            ),
            color = WearColors.TextSecondary,
            modifier = Modifier.width(30.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.body2.copy(fontSize = 8.sp),
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}