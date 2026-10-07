@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.dudo.fishing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dudo.fishing.data.CatchRecord
import com.dudo.fishing.data.DayConditions
import com.dudo.fishing.data.Settings
import com.dudo.fishing.scoring.PointResult
import com.dudo.fishing.scoring.ScoreEngine
import com.dudo.fishing.scoring.SlotScore
import com.dudo.fishing.scoring.Species
import java.time.format.DateTimeFormatter

private val HM = DateTimeFormatter.ofPattern("HH:mm")
private val MD = DateTimeFormatter.ofPattern("M월 d일 (E)")

// ───────────────────────── 홈 ─────────────────────────

@Composable
fun HomeScreen(vm: MainViewModel, onOpen: (String) -> Unit, onSettings: () -> Unit) {
    val st by vm.state.collectAsState()
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("두도 포인트", fontWeight = FontWeight.Bold) },
            actions = {
                IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, "새로고침") }
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "설정") }
            })
    }) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                // 날짜 선택 (오늘/내일/모레)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("오늘", "내일", "모레").forEachIndexed { i, label ->
                        FilterChip(st.dayOffset == i, { vm.setDay(i) }, { Text(label) })
                    }
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(st.species == null, { vm.setSpecies(null) }, { Text("최적 어종") })
                    Species.entries.forEach { s ->
                        FilterChip(st.species == s, { vm.setSpecies(s) }, { Text(s.label) })
                    }
                }
            }
            if (st.loading) item {
                Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator() }
            }
            st.error?.let { e -> item { Text("오류: $e", color = Bad) } }
            st.conditions?.let { c ->
                if (!st.loading) {
                    item { ConditionsCard(c) }
                    c.messages.forEach { m -> item { Notice(m) } }
                    item { Text("오늘의 추천 순위", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    items(st.results, key = { it.point.id }) { r -> PointRow(r) { onOpen(r.point.id) } }
                    item {
                        Text(
                            "※ 점수는 공공 데이터와 일반적인 경험칙으로 계산한 참고값입니다. 갯바위 출조 전 기상특보·현장 상황을 반드시 확인하세요.",
                            style = MaterialTheme.typography.bodySmall, color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConditionsCard(c: DayConditions) {
    val noon = c.weather.firstOrNull { it.time.toLocalDate() == c.date && it.time.hour == 12 } ?: c.weather.firstOrNull()
    val dayTides = c.tides.filter { it.time.toLocalDate() == c.date }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(c.date.format(MD), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Stat("물때", c.mulName + if (c.tideIsEstimated) "*" else "")
                Stat("수온", "%.1f°C".format(c.waterTemp) + if (c.waterTempIsEstimated) "*" else "")
                noon?.let {
                    Stat("바람(정오)", "${ScoreEngine.compass(it.windDir)} %.1fm/s".format(it.windSpeed))
                    it.wave?.let { w -> Stat("파고", "%.1fm".format(w)) }
                }
            }
            Text(
                "만조 " + dayTides.filter { it.isHigh }.joinToString(", ") { it.time.format(HM) } +
                        "   간조 " + dayTides.filter { !it.isHigh }.joinToString(", ") { it.time.format(HM) },
                style = MaterialTheme.typography.bodyMedium
            )
            Text("일출 ${c.sunrise.format(HM)} · 일몰 ${c.sunset.format(HM)}", style = MaterialTheme.typography.bodyMedium)
            if (c.tideIsEstimated || c.waterTempIsEstimated)
                Text("* 추정값", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Notice(text: String) {
    Row(
        Modifier.fillMaxWidth().background(Mid.copy(alpha = 0.12f), RoundedCornerShape(8.dp)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("ⓘ ", color = Mid)
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PointRow(r: PointResult, onClick: () -> Unit) {
    val b = r.best
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ScoreBadge(b.score)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(r.point.name, fontWeight = FontWeight.Bold)
                Text(
                    "${r.species.label} · ${b.slot.label}(${b.slot.rangeText}) · ${b.tidePhase}",
                    style = MaterialTheme.typography.bodySmall
                )
                b.reasons.maxByOrNull { it.delta }?.let {
                    Text("👍 ${it.text}", style = MaterialTheme.typography.bodySmall, color = Good)
                }
                b.danger?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = Bad, modifier = Modifier.size(14.dp))
                        Text(" $it", style = MaterialTheme.typography.bodySmall, color = Bad)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreBadge(score: Int, size: Int = 52) {
    Box(
        Modifier.size(size.dp).background(scoreColor(score), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text("$score", color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size / 2.6).sp)
    }
}

// ───────────────────────── 상세 ─────────────────────────

@Composable
fun DetailScreen(vm: MainViewModel, pointId: String, onBack: () -> Unit) {
    val st by vm.state.collectAsState()
    val r = st.results.firstOrNull { it.point.id == pointId }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(r?.point?.name ?: "") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } })
    }) { pad ->
        if (r == null) return@Scaffold
        var selected by remember(pointId) { mutableStateOf(r.best.slot) }
        val slot = r.slots.first { it.slot == selected }
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("${r.point.area} · 수심 ${r.point.depth} · ${r.point.bottom}", style = MaterialTheme.typography.bodyMedium)
            Text("대표 어종: ${r.point.species.joinToString()}", style = MaterialTheme.typography.bodyMedium)
            Text("바라보는 방향: ${ScoreEngine.compass(r.point.facingDeg)}쪽 · 지형: ${r.point.terrain} · ${r.point.note}", style = MaterialTheme.typography.bodySmall)
            if (!r.point.coordVerified) Notice("이 포인트 좌표는 대략적인 값이에요. points.json에서 실제 위치로 고쳐주세요.")

            HorizontalDivider()
            Text("${r.species.label} 시간대별 점수", fontWeight = FontWeight.Bold)
            r.slots.forEach { s -> SlotBar(s, s.slot == selected) { selected = s.slot } }

            HorizontalDivider()
            Text("${slot.slot.label}(${slot.slot.rangeText}) 점수 근거", fontWeight = FontWeight.Bold)
            slot.danger?.let { Text("⚠ $it", color = Bad, fontWeight = FontWeight.Bold) }
            Text(
                "바람 ${ScoreEngine.compass(slot.windDir)} %.1fm/s".format(slot.windSpeed) +
                        (slot.wave?.let { " · 파고 %.1fm".format(it) } ?: "") + " · ${slot.tidePhase}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text("가감 합계 %+d → %d점".format(slot.reasons.sumOf { it.delta }, slot.score) + if (slot.danger != null) " (위험 조건으로 15점 제한)" else "", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            slot.reasons.forEach { reason ->
                Row(Modifier.fillMaxWidth()) {
                    Text(reason.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    val d = reason.delta
                    Text(
                        if (d > 0) "+$d" else "$d",
                        color = if (d > 0) Good else if (d < 0) Bad else Color.Gray,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            HorizontalDivider()
            CatchLogForm(r.point.species, slot) { catches, note -> vm.addCatch(r.point, slot, catches, note) }

            HorizontalDivider()
            val records = vm.recordsFor(r.point)
            Text("이 포인트 조과 기록 (${records.size}건)", fontWeight = FontWeight.Bold)
            if (records.isEmpty()) Text("아직 기록이 없어요. 낚시 후 위에서 기록을 남기면 다음부터 비슷한 조건일 때 점수에 반영돼요.",
                style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            records.forEach { rec -> RecordRow(rec, onDelete = if (rec.byUser) ({ vm.deleteCatch(rec.id) }) else null) }
        }
    }
}

/** 낚시 후 조과를 남기는 입력칸. 저장할 때 지금 시간대의 바람·파고·수온이 함께 기록된다. */
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
            Text(rec.catches.entries.joinToString { "${it.key} ${it.value}마리" }, fontWeight = FontWeight.SemiBold)
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

@Composable
private fun SlotBar(s: SlotScore, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${s.slot.label}", Modifier.width(44.dp), fontWeight = FontWeight.SemiBold)
        Text(s.slot.rangeText, Modifier.width(72.dp), style = MaterialTheme.typography.bodySmall)
        LinearProgressIndicator(
            progress = { s.score / 100f },
            modifier = Modifier.weight(1f).height(10.dp),
            color = scoreColor(s.score),
            trackColor = Color.LightGray.copy(alpha = 0.4f),
        )
        Text("  ${s.score}", Modifier.width(40.dp), fontWeight = FontWeight.Bold)
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
