package com.dudo.fishing.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dudo.fishing.data.BeachApi
import com.dudo.fishing.data.CatchRecord
import com.dudo.fishing.data.HistoryRepository
import com.dudo.fishing.data.ConditionsRepository
import com.dudo.fishing.data.DayConditions
import com.dudo.fishing.data.FishingPoint
import com.dudo.fishing.data.PointRepository
import com.dudo.fishing.data.Settings
import com.dudo.fishing.scoring.PointResult
import com.dudo.fishing.scoring.ScoreContext
import com.dudo.fishing.scoring.ScoreEngine
import com.dudo.fishing.scoring.SlotScore
import com.dudo.fishing.scoring.Species
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class UiState(
    val loading: Boolean = true,
    val dayOffset: Int = 0,
    val species: Species? = null,          // null = 포인트별 최적 어종
    val conditions: DayConditions? = null,
    val results: List<PointResult> = emptyList(),
    val error: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val settings = Settings(app)
    private val repo = ConditionsRepository(settings, BeachApi.loadBeaches(app))
    private var points: List<FishingPoint> = PointRepository.load(app)
    private val historyRepo = HistoryRepository(app)
    private var history: List<CatchRecord> = emptyList()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init { refresh() }

    fun setDay(offset: Int) { _state.update { it.copy(dayOffset = offset) }; refresh() }

    fun setSpecies(s: Species?) {
        _state.update { it.copy(species = s) }
        _state.value.conditions?.let { rank(it) }
    }

    fun refresh() {
        val date = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(_state.value.dayOffset.toLong())
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                if (history.isEmpty()) history = runCatching { historyRepo.all() }.getOrElse { historyRepo.bundled() + historyRepo.userRecords() }
                rank(repo.load(date))
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "알 수 없는 오류") }
            }
        }
    }

    /** 데이터 현황: (포인트 기록, 두도 전체 조황 일수, 내 기록) */
    fun dataSummary(): Triple<Int, Int, Int> = Triple(
        history.count { !it.byUser && com.dudo.fishing.scoring.Factors.isPointRecord(it) },
        history.count { !it.byUser && !com.dudo.fishing.scoring.Factors.isPointRecord(it) },
        history.count { it.byUser },
    )

    /** 내 조과 기록을 JSON으로 공유 (카톡·메일로 보내 앱 데이터에 합칠 수 있게) */
    fun shareMyRecords(context: android.content.Context) {
        val json = historyRepo.exportUserRecords()
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, "두도 포인트 내 조과 기록")
            putExtra(android.content.Intent.EXTRA_TEXT, json)
        }
        context.startActivity(android.content.Intent.createChooser(send, "내 조과 기록 보내기").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** 이 포인트(또는 같은 방향) 관련 조과 기록 */
    fun recordsFor(point: FishingPoint): List<CatchRecord> =
        history.filter { it.pointId == point.id || (it.pointId == null && it.sideFacingDeg != null &&
                com.dudo.fishing.scoring.Factors.angleDiff(it.sideFacingDeg, point.facingDeg) <= 50) }
            .sortedByDescending { it.date }

    /** 지금 조건과 함께 내 조과를 저장하고 점수를 다시 계산 */
    fun addCatch(point: FishingPoint, slot: SlotScore, catches: Map<String, Int>, note: String) {
        val c = _state.value.conditions ?: return
        val rec = CatchRecord(
            id = "user_" + System.currentTimeMillis(),
            date = c.date, startHour = slot.slot.startHour, endHour = slot.slot.endHour,
            pointId = point.id, sideFacingDeg = null, catches = catches.filterValues { it > 0 },
            note = note, source = "내 기록", url = null, byUser = true,
            windSpeed = slot.windSpeed, windDir = slot.windDir, wave = slot.wave, waterTemp = c.waterTemp,
        )
        if (rec.catches.isEmpty()) return
        historyRepo.addUserRecord(rec)
        history = history + rec
        rank(c)
    }

    /** 지도에서 탭한 위치로 포인트 좌표를 옮겨 저장 */
    fun movePoint(id: String, lat: Double, lng: Double) {
        PointRepository.saveLocation(getApplication(), id, lat, lng)
        points = PointRepository.load(getApplication())
        _state.value.conditions?.let { rank(it) }
    }

    fun resetPoint(id: String) {
        PointRepository.resetLocation(getApplication(), id)
        points = PointRepository.load(getApplication())
        _state.value.conditions?.let { rank(it) }
    }

    fun deleteCatch(id: String) {
        historyRepo.deleteUserRecord(id)
        history = history.filterNot { it.id == id }
        _state.value.conditions?.let { rank(it) }
    }

    private fun rank(c: DayConditions) {
        val sp = _state.value.species
        val ctx = ScoreContext(history, points.associateBy { it.id })
        val results = points.map { p ->
            if (sp == null) ScoreEngine.bestForPoint(p, c, ctx) else ScoreEngine.evaluate(p, sp, c, ctx)
        }.sortedByDescending { it.best.score }
        _state.update { it.copy(loading = false, conditions = c, results = results) }
    }
}
