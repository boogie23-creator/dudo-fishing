@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.dudo.fishing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Phishing
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dudo.fishing.data.CatchRecord
import com.dudo.fishing.data.DayConditions
import com.dudo.fishing.data.Settings
import com.dudo.fishing.scoring.PointResult
import com.dudo.fishing.scoring.Reason
import com.dudo.fishing.scoring.ScoreEngine
import com.dudo.fishing.scoring.SlotScore
import com.dudo.fishing.scoring.Species
import com.dudo.fishing.scoring.Factors
import com.dudo.fishing.scoring.Tactics
import com.dudo.fishing.scoring.Tip
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val HM = DateTimeFormatter.ofPattern("HH:mm")
private val MD = DateTimeFormatter.ofPattern("M월 d일 (E)")
private fun nowSeoul() = LocalDateTime.now(ZoneId.of("Asia/Seoul"))

// ───────────────────────── 홈 ─────────────────────────

@Composable
fun HomeScreen(vm: MainViewModel, onOpen: (String) -> Unit, onSettings: () -> Unit) {
    val st by vm.state.collectAsState()
    var mapMode by remember { mutableStateOf(false) }
    var showNotices by remember { mutableStateOf(false) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding()),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { HomeHeader(st.conditions, st.dayOffset, vm::setDay, vm::refresh, onSettings) }

            item {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("대상어", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Row(Modifier.weight(1f).horizontalScrollSafe(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SeaChip("최적", st.species == null) { vm.setSpecies(null) }
                        Species.entries.forEach { s -> SeaChip(s.label, st.species == s, speciesIcon(s)) { vm.setSpecies(s) } }
                    }
                }
            }

            if (st.loading) item {
                Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) { CircularProgressIndicator(color = Tide) }
            }
            st.error?.let { e -> item { Text("오류: $e", color = Bad, modifier = Modifier.padding(horizontal = 16.dp)) } }

            val c = st.conditions
            if (c != null && !st.loading) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("추천 포인트", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        SeaChip("목록", !mapMode, Icons.Default.ViewList) { mapMode = false }
                        Spacer(Modifier.width(6.dp))
                        SeaChip("지도", mapMode, Icons.Default.Map) { mapMode = true }
                    }
                }
                if (mapMode) {
                    item {
                        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(440.dp), shape = RoundedCornerShape(20.dp)) {
                            PointsMap(
                                pins = st.results.map { MapPin(it.point, it.dayScore) },
                                selectedId = null, zoom = 17.2,
                                modifier = Modifier.fillMaxSize(),
                                onPinClick = onOpen,
                            )
                        }
                        Text("숫자는 포인트 번호, 색은 점수 (주황 강력추천 · 초록 좋음 · 노랑 보통 · 빨강 비추천)",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
                    }
                } else {
                    itemsIndexed(st.results, key = { _, r -> r.point.id }) { i, r ->
                        if (i == 0) TopPickCard(r) { onOpen(r.point.id) } else PointRow(i + 1, r) { onOpen(r.point.id) }
                    }
                }

                // 윈디 바람·파도 흐름 (보기 전용)
                item { WindyCard(lat = 35.0488, lng = 129.0150, modifier = Modifier.padding(horizontal = 16.dp)) }

                if (c.messages.isNotEmpty()) item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Row(Modifier.clickable { showNotices = !showNotices }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("데이터 연결 상태 (${c.messages.size})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Icon(if (showNotices) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                        if (showNotices) c.messages.forEach { Notice(it) }
                    }
                }
                item {
                    val (pt, day, mine) = vm.dataSummary()
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("조과 데이터", style = MaterialTheme.typography.titleMedium)
                            Text("포인트 기록 ${pt}건 · 두도 전체 조황 ${day}일 · 내 기록 ${mine}건", fontWeight = FontWeight.Bold)
                            Text("포인트 기록만 포인트 순위를 바꾸고, 두도 전체 조황은 모든 포인트에 똑같이 적용돼요. 낚시 후 포인트 화면에서 조과를 남길수록 정확해져요.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (mine > 0) Text("내 기록 보내기", color = Tide, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { vm.shareMyRecords(ctx) }.padding(top = 4.dp))
                        }
                    }
                }
                item {
                    Text(
                        "※ 점수는 공공 데이터·조행기·밴드 조황으로 계산한 참고값입니다. 갯바위 출조 전 기상특보와 현장 상황을 꼭 확인하세요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

/** 가로로 넘칠 때 스크롤되도록 */
@Composable
private fun Modifier.horizontalScrollSafe(): Modifier = this.then(Modifier.horizontalScroll(rememberScrollState()))

@Composable
private fun HomeHeader(c: DayConditions?, dayOffset: Int, onDay: (Int) -> Unit, onRefresh: () -> Unit, onSettings: () -> Unit) {
    WaveHeader {
        Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 34.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("두도 포인트", style = MaterialTheme.typography.headlineSmall, color = Foam)
                    Text("부산 송도 두도 · 갯바위 찌낚시", color = Shallow, fontSize = 13.sp)
                }
                IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "새로고침", tint = Foam) }
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "설정", tint = Foam) }
            }
            Spacer(Modifier.height(14.dp))
            // 날짜 선택
            Row(
                Modifier.padding(end = 12.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.10f)).padding(4.dp)
            ) {
                listOf("오늘", "내일", "모레").forEachIndexed { i, label ->
                    val sel = dayOffset == i
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(50))
                            .background(if (sel) Foam else Color.Transparent)
                            .clickable { onDay(i) }.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(label, color = if (sel) Abyss else Foam, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                }
            }
            if (c != null) {
                Spacer(Modifier.height(14.dp))
                Text(c.date.format(MD) + " · " + c.mulName + if (c.tideIsEstimated) " (추정)" else "", color = Foam, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(10.dp))
                val noon = c.weather.firstOrNull { it.time.toLocalDate() == c.date && it.time.hour == 12 } ?: c.weather.firstOrNull()
                Row(Modifier.padding(end = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassStat("수온", "%.1f°".format(c.waterTemp) + if (c.waterTempIsEstimated) "*" else "", Modifier.weight(1f)) {
                        Icon(Icons.Default.Thermostat, null, tint = Shallow, modifier = Modifier.size(16.dp))
                    }
                    noon?.let { w ->
                        GlassStat("바람(정오)", "%.1f".format(w.windSpeed) + "m/s", Modifier.weight(1.2f)) { WindArrow(w.windDir, Shallow) }
                        GlassStat("파고", w.wave?.let { "%.1fm".format(it) } ?: "-", Modifier.weight(1f)) {
                            Icon(Icons.Default.Waves, null, tint = Shallow, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                TideChart(c.tides, c.date, nowSeoul(), Modifier.padding(end = 12.dp))
                val dayTides = c.tides.filter { it.time.toLocalDate() == c.date }
                Text(
                    "만조 " + dayTides.filter { it.isHigh }.joinToString(" · ") { it.time.format(HM) } +
                            "   간조 " + dayTides.filter { !it.isHigh }.joinToString(" · ") { it.time.format(HM) } +
                            "   일출 ${c.sunrise.format(HM)} 일몰 ${c.sunset.format(HM)}",
                    color = Foam.copy(alpha = 0.8f), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun SeaChip(label: String, selected: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    val bg = if (selected) Tide else MaterialTheme.colorScheme.surface
    val fg = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(bg)
            .border(1.dp, if (selected) Tide else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(4.dp)) }
        Text(label, color = fg, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun Notice(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("ⓘ ", color = Tide)
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

/** 오늘의 1순위: 크게 강조 */
@Composable
private fun TopPickCard(r: PointResult, onClick: () -> Unit) {
    val b = r.best
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(Modifier.background(Brush.linearGradient(listOf(Tide, DeepSea))).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("오늘의 1순위", color = Coral, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    Text(r.point.name, color = Foam, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(speciesIcon(r.species), null, tint = Shallow, modifier = Modifier.size(16.dp))
                        Text(" ${r.species.label} · 최고 %02d~%02d시 · ${r.profile.tideType}".format(r.bestWindow.first, r.bestWindow.second), color = Foam.copy(alpha = 0.9f), fontSize = 13.sp)
                    }
                    HourStrip(r, light = true)
                    topReason(b)?.let { Text("👍 ${it.text}", color = Foam.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp)) }
                    b.danger?.let { DangerLine(it, Coral) }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface).padding(4.dp)) { ScoreGauge(r.dayScore, 70.dp, 7.dp, showLabel = true) }
                    Text("05~13시 평균", color = Foam.copy(alpha = 0.8f), fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun PointRow(rank: Int, r: PointResult, onClick: () -> Unit) {
    val b = r.best
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$rank", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
            ScoreGauge(r.dayScore, 50.dp, 5.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.point.name, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    TerrainTag(r.point.terrain)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(speciesIcon(r.species), null, tint = Tide, modifier = Modifier.size(14.dp))
                    Text(" ${r.species.label} · 최고 %02d~%02d시 · ${r.profile.tideType}".format(r.bestWindow.first, r.bestWindow.second),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HourStrip(r, light = false)
                topReason(b)?.let { Text(it.text, style = MaterialTheme.typography.bodySmall, color = Good, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                b.danger?.let { DangerLine(it, Bad) }
            }
            WindArrow(b.windDir, MaterialTheme.colorScheme.onSurfaceVariant, 18.dp)
        }
    }
}

/** 05~13시 시간별 확률을 작은 막대로 */
@Composable
private fun HourStrip(r: PointResult, light: Boolean) {
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        r.slots.forEach { s ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val after = s.slot.startHour >= com.dudo.fishing.scoring.TimeSlot.FISHING_END
                Box(Modifier.width(11.dp).height((4 + s.score * 0.22).dp).clip(RoundedCornerShape(3.dp))
                    .background((if (s.danger != null) Bad else scoreColor(s.score)).copy(alpha = if (after) 0.4f else 1f)))
                Text("${s.slot.startHour}", fontSize = 8.sp,
                    color = if (light) Foam.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun topReason(s: SlotScore): Reason? = s.reasons.filter { it.delta > 0 }.maxByOrNull { it.delta }

@Composable
private fun DangerLine(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Icon(Icons.Default.Warning, null, tint = color, modifier = Modifier.size(14.dp))
        Text(" $text", style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TerrainTag(terrain: String) {
    Text(
        terrain, fontSize = 10.sp, color = Tide, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Tide.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 1.dp)
    )
}

// ───────────────────────── 상세 ─────────────────────────

@Composable
fun DetailScreen(vm: MainViewModel, pointId: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val st by vm.state.collectAsState()
    val r = st.results.firstOrNull { it.point.id == pointId } ?: return
    var selected by remember(pointId) { mutableStateOf(r.best.slot) }
    var editMode by remember { mutableStateOf(false) }
    var moved by remember { mutableStateOf(false) }
    val slot = r.slots.first { it.slot == selected }
    val cond = st.conditions
    val flowDeg = cond?.let { c ->
        val center = c.date.atTime(slot.slot.startHour, 0).plusMinutes(((slot.slot.endHour - slot.slot.startHour) * 30).toLong())
        Factors.tideState(center, c.tides)?.takeIf { !it.nearSlack }?.let { if (it.incoming) Factors.FLOOD_FLOW_DEG else Factors.EBB_FLOW_DEG }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState(), enabled = !editMode).navigationBarsPadding()
    ) {
        // ── 위성지도 ──
        Box(Modifier.fillMaxWidth().height(300.dp)) {
            PointsMap(
                pins = st.results.map { MapPin(it.point, it.dayScore) },
                selectedId = pointId, editMode = editMode, flowDeg = flowDeg,
                modifier = Modifier.fillMaxSize(),
                onPinClick = { id -> if (!editMode && id != pointId) onOpen(id) },
                onMapTap = { lat, lng -> vm.movePoint(pointId, lat, lng); moved = true },
            )
            // 위쪽 그림자 + 뒤로가기
            Box(Modifier.fillMaxWidth().height(90.dp).background(Brush.verticalGradient(listOf(Abyss.copy(alpha = 0.7f), Color.Transparent))))
            Row(Modifier.statusBarsPadding().padding(8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.clip(CircleShape).background(Abyss.copy(alpha = 0.55f))) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = Foam)
                }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(if (editMode) Coral else Abyss.copy(alpha = 0.55f))
                        .clickable { editMode = !editMode; moved = false }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.EditLocationAlt, null, tint = Foam, modifier = Modifier.size(16.dp))
                    Text(if (editMode) " 완료" else " 위치 수정", color = Foam, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            // 아래쪽 이름표
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Abyss.copy(alpha = 0.85f))))
                    .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp)
            ) {
                if (editMode) Text(if (moved) "✅ 위치를 저장했어요. 다시 탭하면 또 옮겨져요." else "지도에서 실제 자리를 탭하세요",
                    color = Coral, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(r.point.name, color = Foam, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                Text("${ScoreEngine.compass(r.point.facingDeg)}쪽 공략 · 점선 끝 원 = 공략 지점 · 하늘색 화살표 = ${slot.slot.label} 조류",
                    color = Foam.copy(alpha = 0.8f), fontSize = 12.sp)
            }
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // ── 점수 요약 ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ScoreGauge(r.dayScore, 92.dp, 8.dp, showLabel = true)
                    Text("05~13시 평균", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(speciesIcon(r.species), null, tint = Tide, modifier = Modifier.size(18.dp))
                        Text(" ${r.species.label} · 최고 %02d~%02d시".format(r.bestWindow.first, r.bestWindow.second), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    }
                    Text("선택 ${slot.slot.rangeText}: ${slot.score}점 · ${slot.tidePhase}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WindArrow(slot.windDir, Tide)
                        Text(" ${ScoreEngine.compass(slot.windDir)} %.1fm/s".format(slot.windSpeed) + (slot.wave?.let { " · 파고 %.1fm".format(it) } ?: ""),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                    slot.danger?.let { DangerLine(it, Bad) }
                }
            }

            // ── 포인트 정보 ──
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoPill("성격", r.profile.tideType)
                InfoPill("너울", r.profile.exposureText)
                InfoPill("수심", r.point.depth)
                InfoPill("지형", r.point.terrain)
                InfoPill("바닥", r.point.bottom)
                InfoPill("대표 어종", r.point.species.joinToString())
            }
            WindyButton(r.point.lat, r.point.lng)
            if (!r.point.coordVerified) Notice("위성사진 해안선 기준 위치예요. 실제 자리와 다르면 지도 위 '위치 수정'을 누르고 탭하면 저장돼요.")

            // ── 시간대별 ──
            SectionCard("시간별 확률 (05~18시) – 누르면 근거") {
                Text("순위는 05~13시 평균이에요. 13시 이후는 참고용.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                r.slots.forEach { s -> SlotBar(s, s.slot == selected) { selected = s.slot } }
            }

            // ── 근거 ──
            SectionCard("${slot.slot.label} 점수 근거") {
                Text("가감 합계 %+d → %d점".format(slot.reasons.sumOf { it.delta }, slot.score) +
                        if (slot.danger != null) " (위험 조건으로 15점 제한)" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                slot.reasons.sortedByDescending { it.delta }.forEach { reason -> ReasonRow(reason) }
            }

            // ── 참고: 채비·밑밥 (접힘) ──
            if (cond != null) {
                var showTips by remember(pointId) { mutableStateOf(false) }
                Text(if (showTips) "채비·밑밥 참고 접기 ▲" else "채비·밑밥 참고 보기 ▼", color = Tide, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { showTips = !showTips }.padding(vertical = 4.dp))
                if (showTips) SectionCard(null) { Tactics.build(r.point, r.species, slot, cond).forEach { tip -> TipRow(tip) } }
            }

            SectionCard(null) {
                CatchLogForm(r.point.species, slot) { catches, note -> vm.addCatch(r.point, slot, catches, note) }
            }

            val records = vm.recordsFor(r.point)
            Text("이 포인트 조과 기록 (${records.size}건)", style = MaterialTheme.typography.titleMedium)
            if (records.isEmpty()) Text("아직 기록이 없어요. 낚시 후 위에서 기록을 남기면 다음부터 비슷한 조건일 때 점수에 반영돼요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            records.forEach { rec -> RecordRow(rec, onDelete = if (rec.byUser) ({ vm.deleteCatch(rec.id) }) else null) }
            if (r.point.coordVerified) Text("지도 위치 원래대로", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                modifier = Modifier.clickable { vm.resetPoint(r.point.id) }.padding(vertical = 8.dp))
        }
    }
}

@Composable
private fun SectionCard(title: String?, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            content()
        }
    }
}

@Composable
private fun TipRow(tip: Tip) {
    val (icon, color) = when (tip.kind) {
        Tip.Kind.SAFETY -> Icons.Default.Warning to Bad
        Tip.Kind.SPOT -> Icons.Default.GpsFixed to Coral
        Tip.Kind.CURRENT -> Icons.Default.Waves to Tide
        Tip.Kind.RIG -> Icons.Default.Phishing to DeepSea
        Tip.Kind.CHUM -> Icons.Default.Grain to Tide
        Tip.Kind.BAIT -> Icons.Default.SetMeal to Good
        Tip.Kind.FIX -> Icons.Default.Build to Rock
        Tip.Kind.TIMING -> Icons.Default.Schedule to Mid
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(tip.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (tip.kind == Tip.Kind.SAFETY) Bad else MaterialTheme.colorScheme.onSurface)
            Text(tip.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoPill(label: String, value: String) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text("$label ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ReasonRow(reason: Reason) {
    val d = reason.delta
    val color = if (d > 0) Good else if (d < 0) Bad else MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(reason.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(if (d > 0) "+$d" else "$d", color = color, fontWeight = FontWeight.Bold)
    }
}

/** 시간대 막대: 점수만큼 물이 차오르는 느낌 */
@Composable
private fun SlotBar(s: SlotScore, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (selected) Tide.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.width(78.dp)) {
            Text(s.slot.label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(s.tidePhase, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxWidth(s.score / 100f).height(14.dp).clip(RoundedCornerShape(7.dp))
                .background(Brush.horizontalGradient(listOf(Shallow, scoreColor(s.score)))))
        }
        Text("${s.score}", Modifier.width(40.dp).padding(start = 8.dp), fontWeight = FontWeight.ExtraBold, color = scoreColor(s.score))
        if (s.danger != null) Icon(Icons.Default.Warning, s.danger, tint = Bad, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun CatchLogForm(speciesLabels: List<String>, slot: SlotScore, onSave: (Map<String, Int>, String) -> Unit) {
    val all = (speciesLabels + Species.entries.map { it.label }).distinct()
    var counts by remember(slot) { mutableStateOf(all.associateWith { "" }) }
    var note by remember(slot) { mutableStateOf("") }
    var saved by remember(slot) { mutableStateOf(false) }
    Text("내 조과 기록하기 (${slot.slot.label} ${slot.slot.rangeText})", fontWeight = FontWeight.Bold)
    Text("오늘 이 포인트에서 잡은 마릿수를 넣고 저장하세요. 지금의 물때·바람·파고·수온이 함께 저장돼서, 비슷한 조건이 오면 이 포인트 점수가 올라가요.",
        style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        all.forEach { label ->
            OutlinedTextField(
                value = counts[label] ?: "",
                onValueChange = { v -> counts = counts + (label to v.filter { it.isDigit() }.take(3)) },
                modifier = Modifier.width(110.dp), singleLine = true,
                label = { Text(label) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
    }
    OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("메모 (채비·미끼 등, 선택)") })
    Button(onClick = {
        val m = counts.mapValues { it.value.toIntOrNull() ?: 0 }.filterValues { it > 0 }
        if (m.isNotEmpty()) { onSave(m, note); saved = true; counts = all.associateWith { "" }; note = "" }
    }, Modifier.fillMaxWidth()) { Text("조과 저장") }
    if (saved) Text("✅ 저장했어요. 점수에 바로 반영됐어요.", color = Good, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun RecordRow(rec: CatchRecord, onDelete: (() -> Unit)?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${rec.date} · %02d~%02d시 · ${rec.source}".format(rec.startHour, rec.endHour),
                style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Text(rec.catches.keys.joinToString { "$it ${com.dudo.fishing.scoring.Factors.qtyText(rec, it)}" }, fontWeight = FontWeight.SemiBold)
            val cond = listOfNotNull(
                rec.windSpeed?.let { "바람 %.1fm/s".format(it) },
                rec.wave?.let { "파고 %.1fm".format(it) },
                rec.waterTemp?.let { "수온 %.1f°C".format(it) },
            )
            if (cond.isNotEmpty()) Text(cond.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            if (rec.note.isNotBlank()) Text(rec.note, style = MaterialTheme.typography.bodySmall)
            if (onDelete != null) Text("삭제", color = Bad, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.clickable(onClick = onDelete).padding(top = 4.dp))
        }
    }
}

// ───────────────────────── 설정 ─────────────────────────

@Composable
fun SettingsScreen(settings: Settings, onDone: () -> Unit) {
    var kma by remember { mutableStateOf(settings.userKmaKey) }
    var khoa by remember { mutableStateOf(settings.khoaKey) }
    var temp by remember { mutableStateOf(settings.manualWaterTemp?.toString() ?: "") }
    var beach by remember { mutableStateOf(settings.beachNum?.toString() ?: "") }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("설정") },
            navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } })
    }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("기상청 단기예보 (공공데이터포털 인증키)", fontWeight = FontWeight.Bold)
            Text("data.go.kr에서 '기상청_단기예보 조회서비스' 활용신청 후 받은 일반 인증키", style = MaterialTheme.typography.bodySmall)
            if (settings.hasBuiltInKmaKey)
                Text("✅ 앱에 기본 키가 들어 있어요. 비워두면 기본 키를 써요.", style = MaterialTheme.typography.bodySmall, color = Good)
            OutlinedTextField(kma, { kma = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("인증키") })

            Text("바다누리 인증키 (보통 필요 없음)", fontWeight = FontWeight.Bold)
            Text("만조·간조는 공공데이터포털 키로 국립해양조사원 조석예보(부산)를 먼저 받아요. 구 바다누리 키가 있을 때만 넣으세요.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(khoa, { khoa = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("인증키") })

            Text("수온 관측 해수욕장 번호", fontWeight = FontWeight.Bold)
            Text("기상청 해수욕장 날씨 서비스(같은 인증키)에서 실측 수온과 만조·간조를 받아요. 비워두면 두도에서 가장 가까운 송도해수욕장(268번)부터 차례로 써요.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                beach, { beach = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("해수욕장 번호 (자동)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Text("현장 수온 직접 입력 (선택)", fontWeight = FontWeight.Bold)
            Text("비워두면 부산 연안 월평균 수온으로 계산해요.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                temp, { temp = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("수온 °C") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            Button(onClick = {
                settings.dataGoKrKey = kma
                settings.khoaKey = khoa
                settings.manualWaterTemp = temp.toDoubleOrNull()
                settings.beachNum = beach.toIntOrNull()
                onDone()
            }, Modifier.fillMaxWidth()) { Text("저장") }
        }
    }
}
