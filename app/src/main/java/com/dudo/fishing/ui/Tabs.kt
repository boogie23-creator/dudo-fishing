@file:OptIn(ExperimentalLayoutApi::class)

package com.dudo.fishing.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phishing
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dudo.fishing.BuildConfig
import com.dudo.fishing.data.CatchRecord
import com.dudo.fishing.data.DayConditions
import com.dudo.fishing.scoring.PointResult
import com.dudo.fishing.scoring.ScoreEngine
import com.dudo.fishing.scoring.Species
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val KMD = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
private val KHM = DateTimeFormatter.ofPattern("HH:mm")

/** 포인트 이름 → 지도·배지에 쓰는 짧은 번호 ("두도 1번" → "1", "두도 직벽" → "벽") */
fun pointNo(name: String): String = name.removePrefix("두도 ").removeSuffix("번").let { if (it == "직벽") "벽" else it }

// ───────────────────────── 화면 틀: 아래 탭 ─────────────────────────

@Composable
fun AppShell(
    vm: MainViewModel, tab: String, onTab: (String) -> Unit,
    onOpen: (String) -> Unit, onAddLog: () -> Unit, onSettings: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Night)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (tab) {
                    "map" -> MapTab(vm, onOpen, onBack = { onTab("home") })
                    "log" -> LogTab(vm, onAddLog, onBack = { onTab("home") })
                    "more" -> MoreTab(vm, onSettings, onBack = { onTab("home") })
                    else -> HomeTab(vm, onOpen, onMenu = { onTab("more") }, onMap = { onTab("map") })
                }
            }
            BottomBar(tab, onTab, onAddLog)
        }
    }
}

@Composable
private fun BottomBar(tab: String, onTab: (String) -> Unit, onAddLog: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp).background(Color(0xFFF4F7FA))
                .navigationBarsPadding().height(64.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem("홈", Icons.Default.Home, tab == "home") { onTab("home") }
            NavItem("포인트 지도", Icons.Default.Map, tab == "map") { onTab("map") }
            Spacer(Modifier.weight(1f))
            NavItem("조황 기록", Icons.AutoMirrored.Filled.ListAlt, tab == "log") { onTab("log") }
            NavItem("더보기", Icons.Default.MoreHoriz, tab == "more") { onTab("more") }
        }
        // 가운데 둥근 낚시 버튼 → 조황 기록하기
        Box(
            Modifier.align(Alignment.TopCenter).size(62.dp).shadow(8.dp, CircleShape).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF1C4A6B), Color(0xFF0B2236))))
                .border(3.dp, Color(0xFFF4F7FA), CircleShape)
                .clickable(onClick = onAddLog),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Phishing, "조황 기록하기", tint = Foam, modifier = Modifier.size(30.dp)) }
    }
}

@Composable
private fun RowScope.NavItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val c = if (selected) Night else Color(0xFF7D8B98)
    Column(
        Modifier.weight(1f).fillMaxHeight().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, label, tint = c, modifier = Modifier.size(24.dp))
        Text(label, color = c, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

// ───────────────────────── 공통 조각 ─────────────────────────

/** 짙은 남색 카드 (얇은 테두리) */
@Composable
fun NavyCard(modifier: Modifier = Modifier, padding: Dp = 14.dp, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel)
            .border(1.dp, Line, RoundedCornerShape(18.dp)).padding(padding),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) { content() }
}

/** 어종 필터 칩: 선택하면 청록 바탕 */
@Composable
fun FishChip(label: String, selected: Boolean, fish: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) Aqua else Panel)
            .border(1.dp, if (selected) Aqua else Line, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (fish != null) { FishIcon(fish, 13.dp); Spacer(Modifier.width(5.dp)) }
        Text(label, color = if (selected) Night else Foam, fontSize = 13.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium)
    }
}

@Composable
fun SpeciesChips(selected: Species?, onSelect: (Species?) -> Unit, firstLabel: String = "전체", modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        FishChip(firstLabel, selected == null) { onSelect(null) }
        Species.entries.forEach { s -> FishChip(s.label, selected == s, s.label) { onSelect(s) } }
    }
}

@Composable
private fun SectionTitle(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Foam, modifier = Modifier.weight(1f))
        if (action != null) Row(Modifier.clickable { onAction?.invoke() }, verticalAlignment = Alignment.CenterVertically) {
            Text(action, color = Mist, fontSize = 12.sp)
            Icon(Icons.Default.ChevronRight, null, tint = Mist, modifier = Modifier.size(16.dp))
        }
    }
}

/** 제목 가운데 + 왼쪽 뒤로 + 오른쪽 버튼 */
@Composable
private fun CenterBar(title: String, onBack: () -> Unit, action: @Composable (() -> Unit)? = null) {
    Box(Modifier.fillMaxWidth().statusBarsPadding().height(54.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = Foam)
        }
        Text(title, color = Foam, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.align(Alignment.Center))
        if (action != null) Box(Modifier.align(Alignment.CenterEnd)) { action() }
    }
}

@Composable
private fun StatTile(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(Panel2).padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Aqua, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, color = Foam, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Text(value, color = Foam, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 날짜 칩: 오늘 ~ 6일 뒤 */
@Composable
private fun DayChips(dayOffset: Int, onDay: (Int) -> Unit) {
    val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        (0 until 7).forEach { i ->
            val d = today.plusDays(i.toLong())
            val label = listOf("오늘", "내일", "모레").getOrNull(i) ?: "${d.monthValue}/${d.dayOfMonth}(${"월화수목금토일"[d.dayOfWeek.value - 1]})"
            FishChip(label, dayOffset == i) { onDay(i) }
        }
    }
}

private fun mulText(c: DayConditions) = c.mulName + when {
    c.mulName == "조금" || c.mulName == "무쉬" -> ""
    c.tideRangeFactor >= 0.7 -> " (사리)"
    c.tideRangeFactor <= 0.3 -> " (조금)"
    else -> ""
}

// ───────────────────────── 홈 ─────────────────────────

@Composable
fun HomeTab(vm: MainViewModel, onOpen: (String) -> Unit, onMenu: () -> Unit, onMap: () -> Unit) {
    val st by vm.state.collectAsState()
    var showNotices by remember { mutableStateOf(false) }
    val c = st.conditions
    LazyColumn(Modifier.fillMaxSize().background(Night), contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { HomeHero(onMenu, onBell = { showNotices = !showNotices }, onRefresh = vm::refresh) }
        if (showNotices && c != null) item {
            NavyCard(Modifier.padding(horizontal = 16.dp)) {
                Text("데이터 연결 상태", fontWeight = FontWeight.Bold, color = Foam)
                if (c.messages.isEmpty()) Text("모든 자료를 정상으로 받았어요.", color = Mist, fontSize = 13.sp)
                c.messages.forEach { Text("· $it", color = Mist, fontSize = 12.sp) }
            }
        }
        item { DayChips(st.dayOffset, vm::setDay) }
        if (st.dayOffset >= 3) item {
            Text("4일째부터는 바람·파도 예보가 자주 바뀌어 신뢰 낮음 (물때는 정확)", color = Shallow, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
        }
        if (st.loading) item { Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) { CircularProgressIndicator(color = Aqua) } }
        st.error?.let { e -> item { Text("오류: $e", color = Bad, modifier = Modifier.padding(horizontal = 16.dp)) } }
        if (c != null && !st.loading) {
            item { SeaTodayCard(c, st.dayOffset) }
            st.results.firstOrNull()?.let { top -> item { BiteIndexCard(top, st.dayOffset) { onOpen(top.point.id) } } }
            item { SpeciesChips(st.species, vm::setSpecies, firstLabel = "최적 어종") }
            item { SectionTitle("추천 포인트 TOP 3", "지금 가기 좋은 포인트", onMap) }
            item {
                NavyCard(Modifier.padding(horizontal = 16.dp), padding = 8.dp) {
                    st.results.take(3).forEachIndexed { i, r -> TopRow(i + 1, r) { onOpen(r.point.id) } }
                }
            }
            if (st.results.size > 3) {
                item { SectionTitle("다른 포인트") }
                item {
                    NavyCard(Modifier.padding(horizontal = 16.dp), padding = 6.dp) {
                        st.results.drop(3).forEachIndexed { i, r -> SmallRow(i + 4, r) { onOpen(r.point.id) } }
                    }
                }
            }
            item { WindyCard(lat = 35.0488, lng = 129.0150, modifier = Modifier.padding(horizontal = 16.dp)) }
            item {
                Text("※ 점수는 공공 데이터·조행기·밴드 조황으로 계산한 참고값입니다. 갯바위 출조 전 기상특보와 현장 상황을 꼭 확인하세요.",
                    color = Mist, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun HomeHero(onMenu: () -> Unit, onBell: () -> Unit, onRefresh: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(300.dp)) {
        SeaScene(Modifier.fillMaxSize(), seed = 3, islandScale = 1.25f)
        // 위·아래 어둡게 → 글자와 본문이 자연스럽게 이어짐
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Night.copy(alpha = 0.75f), Color.Transparent, Color.Transparent, Night), startY = 0f)))
        Row(Modifier.statusBarsPadding().padding(horizontal = 4.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, "더보기", tint = Foam) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "새로고침", tint = Foam) }
            IconButton(onClick = onBell) { Icon(Icons.Default.Notifications, "데이터 상태", tint = Foam) }
        }
        Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BrandBlock(64.dp)
            Spacer(Modifier.height(26.dp))
            Text("오늘도, 좋은 바다가\n당신을 기다립니다.", color = Foam, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 21.sp,
                fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SeaTodayCard(c: DayConditions, dayOffset: Int) {
    val dayW = c.weather.filter { it.time.toLocalDate() == c.date }
    val noon = dayW.firstOrNull { it.time.hour == 12 } ?: dayW.firstOrNull() ?: c.weather.firstOrNull()
    val temps = dayW.filter { it.time.hour in 5..18 }.mapNotNull { it.temp }
    val rainy = dayW.filter { it.time.hour in 5..13 }.any { it.precipType in 1..4 && it.rainProb >= 50 }
    val cloudy = (noon?.rainProb ?: 0) >= 30
    val (skyIcon, skyText, skyColor) = when {
        rainy -> Triple(Icons.Default.Umbrella, "비 소식", Shallow)
        cloudy -> Triple(Icons.Default.Cloud, "구름", Color(0xFFCFE1EC))
        else -> Triple(Icons.Default.WbSunny, "맑음", Gold)
    }
    var showTide by remember { mutableStateOf(false) }
    NavyCard(Modifier.padding(horizontal = 16.dp).offset(y = (-2).dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(listOf("오늘의 바다", "내일의 바다", "모레의 바다").getOrNull(dayOffset) ?: "그날의 바다", color = Foam, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Text(c.date.format(KMD), color = Mist, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Default.LocationOn, null, tint = Mist, modifier = Modifier.size(15.dp))
            Text("두도 근해", color = Mist, fontSize = 12.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(skyIcon, skyText, tint = skyColor, modifier = Modifier.size(52.dp))
            Spacer(Modifier.width(12.dp))
            Text(noon?.temp?.let { "%.0f°".format(it) } ?: "–", color = Foam, fontSize = 44.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(skyText, color = Foam, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (temps.isNotEmpty()) Text("최고 %.0f° / 최저 %.0f°".format(temps.max(), temps.min()), color = Mist, fontSize = 13.sp)
                if (c.weatherIsDemo) Text("예보 없음 · 예시 값", color = Coral, fontSize = 11.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatTile(Icons.Default.Air, "바람", noon?.let { "${ScoreEngine.compass(it.windDir)} %.1f".format(it.windSpeed) + "m/s" } ?: "–", Modifier.weight(1.15f))
            StatTile(Icons.Default.Waves, "파고", noon?.wave?.let { "%.1f m".format(it) } ?: "–", Modifier.weight(1f))
            StatTile(Icons.Default.Thermostat, "수온", "%.1f°C".format(c.waterTemp) + if (c.waterTempIsEstimated) "*" else "", Modifier.weight(1f))
            StatTile(Icons.Default.DarkMode, "물때", mulText(c), Modifier.weight(1.1f))
        }
        Text(if (showTide) "물때 그래프 접기 ▲" else "만조·간조 시간 보기 ▼", color = Aqua, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { showTide = !showTide })
        if (showTide) {
            TideChart(c.tides, c.date, java.time.LocalDateTime.now(ZoneId.of("Asia/Seoul")), light = true)
            val dayTides = c.tides.filter { it.time.toLocalDate() == c.date }
            Text("만조 " + dayTides.filter { it.isHigh }.joinToString(" · ") { it.time.format(KHM) } +
                    "   간조 " + dayTides.filter { !it.isHigh }.joinToString(" · ") { it.time.format(KHM) } +
                    "\n일출 ${c.sunrise.format(KHM)} · 일몰 ${c.sunset.format(KHM)}" + if (c.tideIsEstimated) " · 물때 추정" else "",
                color = Mist, fontSize = 12.sp)
        }
    }
}

@Composable
private fun BiteIndexCard(top: PointResult, dayOffset: Int, onClick: () -> Unit) {
    val score = top.dayScore
    val msg = when {
        score >= 75 -> "지금은 낚시하기 좋은 날!\n활발한 입질이 예상됩니다."
        score >= 62 -> "입질을 기대해 볼 만한 날이에요."
        score >= 50 -> "무난한 날 – 물때 좋은 시간을 노려보세요."
        else -> "입질이 약한 날이에요. 무리하지 마세요."
    }
    Box(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .border(1.5.dp, Aqua.copy(alpha = 0.7f), RoundedCornerShape(20.dp)).clickable(onClick = onClick)
    ) {
        Canvas(Modifier.matchParentSize()) { drawFishSchool() }
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (dayOffset == 0) "오늘의 입질 지수" else "이날의 입질 지수", color = Foam, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.WorkspacePremium, null, tint = Gold, modifier = Modifier.size(18.dp))
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Icon(Icons.Default.WorkspacePremium, null, tint = Gold, modifier = Modifier.size(40.dp).padding(bottom = 10.dp))
                Spacer(Modifier.width(8.dp))
                Text("$score", color = Foam, fontSize = 64.sp, fontWeight = FontWeight.ExtraBold)
                Text(" / 100", color = Foam.copy(alpha = 0.85f), fontSize = 18.sp, modifier = Modifier.padding(bottom = 14.dp))
                Spacer(Modifier.width(10.dp))
                Text(scoreLabel(score), color = Night, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(bottom = 18.dp).clip(RoundedCornerShape(50)).background(Aqua).padding(horizontal = 10.dp, vertical = 3.dp))
            }
            Text(msg, color = Foam, fontSize = 14.sp, lineHeight = 20.sp)
            Text("1순위 ${top.point.name} · ${top.species.label} · 최고 %02d~%02d시 (05~13시 평균)".format(top.bestWindow.first, top.bestWindow.second),
                color = Foam.copy(alpha = 0.75f), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** 순위 배지 (1 금 · 2 은 · 3 동) */
@Composable
private fun RankBadge(rank: Int, modifier: Modifier = Modifier) {
    val bg = when (rank) { 1 -> Gold; 2 -> Color(0xFFD4DCE3); 3 -> Color(0xFFD9A06B); else -> Panel2 }
    Box(modifier.size(24.dp).clip(CircleShape).background(bg).border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape), contentAlignment = Alignment.Center) {
        Text("$rank", color = if (rank <= 3) Night else Foam, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun ScorePill(score: Int) {
    Text("$score", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(scoreColor(score)).padding(horizontal = 10.dp, vertical = 3.dp))
}

@Composable
private fun TopRow(rank: Int, r: PointResult, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel2.copy(alpha = 0.6f)).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(112.dp).height(78.dp)) {
            SeaScene(Modifier.fillMaxSize(), seed = r.point.id.hashCode() and 0xff)
            RankBadge(rank, Modifier.padding(6.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.point.name, color = Foam, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                ScorePill(r.dayScore)
            }
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                r.point.species.take(3).forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) { FishIcon(s, 12.dp); Text(" $s", color = Mist, fontSize = 11.sp) }
                }
            }
            Text("${r.species.label} · 최고 %02d~%02d시 · ${r.profile.tideType}".format(r.bestWindow.first, r.bestWindow.second),
                color = Aqua, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.ChevronRight, null, tint = Mist, modifier = Modifier.padding(end = 6.dp))
    }
}

@Composable
private fun SmallRow(rank: Int, r: PointResult, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$rank", color = Mist, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp))
        Column(Modifier.weight(1f)) {
            Text(r.point.name, color = Foam, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("${r.species.label} · 최고 %02d~%02d시 · ${r.profile.tideType}".format(r.bestWindow.first, r.bestWindow.second),
                color = Mist, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            r.best.danger?.let { Text("⚠ $it", color = Coral, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        ScorePill(r.dayScore)
        Icon(Icons.Default.ChevronRight, null, tint = Mist)
    }
}

// ───────────────────────── 포인트 지도 ─────────────────────────

@Composable
fun MapTab(vm: MainViewModel, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val st by vm.state.collectAsState()
    val favs by vm.favorites.collectAsState()
    var selId by remember { mutableStateOf<String?>(null) }
    val sel = st.results.firstOrNull { it.point.id == selId } ?: st.results.firstOrNull()
    Column(Modifier.fillMaxSize().background(Night)) {
        CenterBar("포인트 지도", onBack)
        SpeciesChips(st.species, vm::setSpecies, firstLabel = "전체")
        Spacer(Modifier.height(10.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (st.results.isNotEmpty()) PointsMap(
                pins = st.results.map { MapPin(it.point, it.dayScore) },
                selectedId = sel?.point?.id, zoom = 17.2,
                modifier = Modifier.fillMaxSize(),
                onPinClick = { selId = it },
            ) else Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Aqua) }
            MapLegend(Modifier.align(Alignment.TopEnd).padding(10.dp))
            if (sel != null) PointSheet(sel, st.conditions, sel.point.id in favs,
                onFav = { vm.toggleFavorite(sel.point.id) }, onOpen = { onOpen(sel.point.id) },
                modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun MapLegend(modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(Night.copy(alpha = 0.82f)).border(1.dp, Line, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        listOf(S1Blue to "강력 추천", S2Green to "좋음", S3Yellow to "보통", S4Orange to "낮음", S5Red to "비추천").forEach { (c, t) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(c))
                Text(" $t", color = Foam, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun PointSheet(r: PointResult, c: DayConditions?, fav: Boolean, onFav: () -> Unit, onOpen: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().heightIn(max = 470.dp)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(Night)
            .border(1.dp, Line, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Line))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(scoreColor(r.dayScore)).border(2.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                Text(pointNo(r.point.name), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.point.name, color = Foam, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    ScorePill(r.dayScore)
                }
                Text(r.point.target.ifBlank { r.point.note }, color = Mist, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onFav) {
                Icon(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "즐겨찾기", tint = if (fav) Coral else Foam)
            }
        }
        Box(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(14.dp))) {
            SeaScene(Modifier.fillMaxSize(), seed = r.point.id.hashCode() and 0xff, dusk = true, islandScale = 1.2f)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatTile(Icons.Default.Straighten, "수심", r.point.depth, Modifier.weight(1f))
            StatTile(Icons.Default.Terrain, "지형", r.point.terrain, Modifier.weight(1f))
            StatTile(Icons.Default.Waves, "성격", r.profile.tideType, Modifier.weight(1f))
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel2).padding(10.dp)) {
            Text("주요 어종", color = Mist, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                r.point.species.take(4).forEach { s ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FishIcon(s, 26.dp)
                        Text(s, color = Foam, fontSize = 12.sp)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            InfoBox(Icons.Default.Schedule, "추천 시간", "%02d:00 ~ %02d:00".format(r.bestWindow.first, r.bestWindow.second), Modifier.weight(1f))
            InfoBox(Icons.Default.DarkMode, "물때", c?.let { mulText(it) } ?: "–", Modifier.weight(1f))
            val cond = r.best.danger ?: r.best.let { b -> "바람 %.1fm/s".format(b.windSpeed) + (b.wave?.let { " · 파고 %.1fm".format(it) } ?: "") }
            InfoBox(Icons.Default.Waves, "주요 조건", cond, Modifier.weight(1.2f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, Line, RoundedCornerShape(14.dp)).clickable(onClick = onFav),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
            ) {
                Icon(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (fav) Coral else Foam, modifier = Modifier.size(18.dp))
                Text(" 즐겨찾기", color = Foam, fontWeight = FontWeight.Bold)
            }
            Row(
                Modifier.weight(1.8f).height(48.dp).clip(RoundedCornerShape(14.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF1E88E5), Color(0xFF1565C0)))).clickable(onClick = onOpen),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.LocationOn, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text(" 포인트 자세히 보기", color = Color.White, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun InfoBox(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Panel2).padding(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Mist, modifier = Modifier.size(14.dp))
            Text(" $label", color = Mist, fontSize = 11.sp)
        }
        Text(value, color = Foam, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
    }
}

// ───────────────────────── 조황 기록 ─────────────────────────

private val SERIES = listOf("감성돔" to Aqua, "벵에돔" to Color(0xFF4C8DFF), "참돔" to Color(0xFFFF6B7A), "기타" to Mist)

@Composable
fun LogTab(vm: MainViewModel, onAdd: () -> Unit, onBack: () -> Unit) {
    val st by vm.state.collectAsState()   // 기록이 추가·삭제되면 다시 그려지도록
    var sp by remember { mutableStateOf<Species?>(null) }
    var mineOnly by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(15) }
    val all = remember(st.results, st.conditions) { vm.allRecords() }
    val recs = all.filter { (!mineOnly || it.byUser) && (sp == null || sp!!.label in it.catches.keys) }
    Column(Modifier.fillMaxSize().background(Night)) {
        CenterBar("조황 기록", onBack) {
            IconButton(onClick = onAdd) { Icon(Icons.Default.Add, "조황 기록하기", tint = Aqua) }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { SpeciesChips(sp, { sp = it }) }
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FishChip("모든 기록", !mineOnly) { mineOnly = false }
                    FishChip("내 기록", mineOnly) { mineOnly = true }
                }
            }
            item {
                val mine = recs.filter { !it.id.startsWith("band_") }.sumOf { r -> if (sp == null) r.catches.values.sum() else r.catches[sp!!.label] ?: 0 }
                val topSp = recs.flatMap { it.catches.keys }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LogStat("기록", "${recs.size}건", if (mine > 0) "확인된 조과 ${mine}마리" else null, Modifier.weight(1f))
                    LogStat("주요 어종", topSp ?: "–", null, Modifier.weight(1f))
                    LogStat("최근 기록", recs.firstOrNull()?.date?.let { "${it.monthValue}월 ${it.dayOfMonth}일" } ?: "–", null, Modifier.weight(1f))
                }
            }
            item { MonthlyChart(recs, sp) }
            item { SectionTitle("최근 조황 기록") }
            if (recs.isEmpty()) item {
                Text("기록이 없어요. 가운데 낚시 버튼이나 아래 '조황 기록하기'로 남겨 보세요.", color = Mist, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp))
            }
            items(recs.take(shown)) { r ->
                LogCard(r, vm.pointName(r.pointId), onDelete = if (r.byUser) ({ vm.deleteCatch(r.id) }) else null)
            }
            if (recs.size > shown) item {
                Text("더 보기 (${recs.size - shown}건)", color = Aqua, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp).clickable { shown += 20 }.padding(vertical = 6.dp))
            }
            item {
                Row(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(54.dp).clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF5BE7EC), Color(0xFF2CC5D2)))).clickable(onClick = onAdd),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Add, null, tint = Night)
                    Text("  조황 기록하기", color = Night, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun LogStat(label: String, value: String, sub: String?, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Panel).border(1.dp, Line, RoundedCornerShape(14.dp)).padding(12.dp)) {
        Text(label, color = Mist, fontSize = 12.sp)
        Text(value, color = Foam, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub != null) Text(sub, color = Aqua, fontSize = 10.sp)
    }
}

/** 최근 12개월 어종별 조황 기록 일수 */
@Composable
private fun MonthlyChart(recs: List<CatchRecord>, sp: Species?) {
    val end = YearMonth.now(ZoneId.of("Asia/Seoul"))
    val months = (11 downTo 0).map { end.minusMonths(it.toLong()) }
    val series = if (sp != null) listOf(sp.label to Aqua) else SERIES
    val data = series.map { (name, color) ->
        Triple(name, color, months.map { m ->
            recs.count { r ->
                YearMonth.from(r.date) == m && when (name) {
                    "기타" -> r.catches.keys.any { it !in listOf("감성돔", "벵에돔", "참돔") }
                    else -> name in r.catches.keys
                }
            }.toFloat()
        })
    }
    val maxV = (data.maxOfOrNull { d -> d.third.maxOrNull() ?: 0f } ?: 0f).coerceAtLeast(4f)
    NavyCard(Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("월별 조황 추이", color = Foam, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text("최근 12개월 · 기록 수", color = Mist, fontSize = 11.sp)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            data.forEach { (n, c, _) ->
                Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).clip(CircleShape).background(c)); Text(" $n", color = Mist, fontSize = 11.sp) }
            }
        }
        Row {
            Column(Modifier.height(130.dp).padding(end = 4.dp), verticalArrangement = Arrangement.SpaceBetween) {
                listOf(maxV, maxV / 2, 0f).forEach { Text("%.0f".format(it), color = Mist, fontSize = 10.sp) }
            }
            Canvas(Modifier.weight(1f).height(130.dp)) {
                val w = size.width; val h = size.height
                listOf(0f, 0.5f, 1f).forEach { f -> drawLine(Line, Offset(0f, h * f), Offset(w, h * f), strokeWidth = 1f) }
                data.forEach { (_, color, vals) ->
                    val pts = vals.mapIndexed { i, v -> Offset(w * i / (vals.size - 1), h - h * v / maxV) }
                    val path = Path().apply { pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
                    drawPath(path, color, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round))
                    pts.forEach { drawCircle(color, 2.6.dp.toPx(), it) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            months.filterIndexed { i, _ -> i % 2 == 1 }.forEach { Text("${it.monthValue}월", color = Mist, fontSize = 10.sp) }
        }
    }
}

@Composable
private fun LogCard(r: CatchRecord, pointName: String?, onDelete: (() -> Unit)?) {
    val main = r.catches.maxByOrNull { it.value }?.key ?: r.catches.keys.firstOrNull() ?: "감성돔"
    val where = pointName ?: when (r.sideFacingDeg) {
        null -> "두도 전체"
        in 225..315 -> "두도 서편"
        in 135..224 -> "두도 남쪽"
        in 45..134 -> "두도 동편"
        else -> "두도 북쪽"
    }
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel)
            .border(1.dp, Line, RoundedCornerShape(16.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.date.format(KMD), color = Foam, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Default.LocationOn, null, tint = Mist, modifier = Modifier.size(14.dp))
            Text(" $where", color = Mist, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (r.byUser) "내 기록" else r.source, color = Mist, fontSize = 10.sp, maxLines = 1)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FishThumb(main, Modifier.width(118.dp).height(78.dp).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    r.catches.keys.forEach { s ->
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).border(1.dp, Line, RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(s, color = Foam, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(" ${com.dudo.fishing.scoring.Factors.qtyText(r, s)}", color = Aqua, fontSize = 12.sp)
                        }
                    }
                }
                Text("%02d~%02d시".format(r.startHour, r.endHour), color = Mist, fontSize = 11.sp)
                val cond = listOfNotNull(
                    r.windSpeed?.let { "바람 %.1fm/s".format(it) },
                    r.wave?.let { "파고 %.1fm".format(it) },
                    r.waterTemp?.let { "수온 %.1f°".format(it) },
                )
                if (cond.isNotEmpty()) Text(cond.joinToString(" · "), color = Mist, fontSize = 11.sp)
            }
        }
        if (r.note.isNotBlank()) Text(r.note, color = Mist, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (onDelete != null) Text("삭제", color = Bad, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onDelete))
    }
}

// ───────────────────────── 조황 기록하기 ─────────────────────────

@Composable
fun AddLogScreen(vm: MainViewModel, onBack: () -> Unit) {
    val st by vm.state.collectAsState()
    var pid by remember { mutableStateOf(st.results.firstOrNull()?.point?.id) }
    val r = st.results.firstOrNull { it.point.id == pid } ?: st.results.firstOrNull()
    var hour by remember(pid) { mutableStateOf(r?.best?.slot?.startHour) }
    val slot = r?.slots?.firstOrNull { it.slot.startHour == hour } ?: r?.best
    Column(Modifier.fillMaxSize().background(Night).navigationBarsPadding()) {
        CenterBar("조황 기록하기", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val c = st.conditions
            if (c == null || r == null || slot == null) {
                Text("예보를 불러오는 중이에요…", color = Mist)
                return@Column
            }
            Text("${c.date.format(KMD)} 기록 · 그때의 물때·바람·파고·수온이 함께 저장돼요. (다른 날짜는 홈에서 날짜를 바꾼 뒤 기록)", color = Mist, fontSize = 12.sp)
            NavyCard {
                Text("포인트", color = Foam, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    st.results.sortedBy { it.point.name }.forEach { p -> FishChip(p.point.name.removePrefix("두도 "), p.point.id == r.point.id) { pid = p.point.id } }
                }
            }
            NavyCard {
                Text("시간", color = Foam, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    r.slots.forEach { s -> FishChip(s.slot.label, s.slot.startHour == slot.slot.startHour) { hour = s.slot.startHour } }
                }
            }
            NavyCard { CatchLogForm(r.point.species, slot) { catches, note -> vm.addCatch(r.point, slot, catches, note) } }
        }
    }
}

// ───────────────────────── 더보기 ─────────────────────────

@Composable
fun MoreTab(vm: MainViewModel, onSettings: () -> Unit, onBack: () -> Unit) {
    val st by vm.state.collectAsState()
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().background(Night)) {
        CenterBar("더보기", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(18.dp))) {
                SeaScene(Modifier.fillMaxSize(), seed = 11, dusk = true)
                Box(Modifier.fillMaxSize().background(Night.copy(alpha = 0.35f)))
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    BrandBlock(44.dp)
                    Text("버전 ${BuildConfig.VERSION_NAME}", color = Foam.copy(alpha = 0.8f), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            MoreItem(Icons.Default.Settings, "설정", "기상청 인증키 · 수온 관측 해수욕장 · 현장 수온", onSettings)
            val (pt, day, mine) = vm.dataSummary()
            NavyCard {
                Text("조과 데이터", color = Foam, fontWeight = FontWeight.ExtraBold)
                Text("포인트 기록 ${pt}건 · 두도 전체 조황 ${day}일 · 내 기록 ${mine}건", color = Foam, fontSize = 13.sp)
                Text("포인트 기록만 포인트 순위를 바꾸고, 두도 전체 조황은 모든 포인트에 똑같이 적용돼요. 조과를 남길수록 정확해져요.", color = Mist, fontSize = 12.sp)
            }
            if (mine > 0) MoreItem(Icons.Default.Share, "내 조과 기록 보내기", "카톡·메일로 보내 앱 기본 데이터에 합칠 수 있어요") { vm.shareMyRecords(ctx) }
            val moved = vm.movedPointCount()
            if (moved > 0) MoreItem(Icons.Default.Share, "고친 포인트 위치 보내기", "${moved}곳") { vm.sharePointLocations(ctx) }
            st.conditions?.let { c ->
                NavyCard {
                    Text("데이터 연결 상태", color = Foam, fontWeight = FontWeight.ExtraBold)
                    if (c.messages.isEmpty()) Text("모든 자료를 정상으로 받았어요.", color = Mist, fontSize = 12.sp)
                    c.messages.forEach { Text("· $it", color = Mist, fontSize = 12.sp) }
                }
            }
            NavyCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = Aqua, modifier = Modifier.size(18.dp))
                    Text("  안내", color = Foam, fontWeight = FontWeight.ExtraBold)
                }
                Text("점수는 기상청·국립해양조사원·Open-Meteo 예보와 조행기·밴드 조황으로 계산한 참고값이에요. 갯바위 출조 전 기상특보와 현장 상황을 꼭 확인하세요.\n제목 글씨: 나눔손글씨 붓 (SIL Open Font License).",
                    color = Mist, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun MoreItem(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).border(1.dp, Line, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(Panel2), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Aqua, modifier = Modifier.size(19.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Foam, fontWeight = FontWeight.Bold)
            Text(sub, color = Mist, fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = Mist)
    }
}
