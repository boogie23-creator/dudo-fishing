package com.dudo.fishing.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dudo.fishing.data.ConditionsRepository
import com.dudo.fishing.data.DayConditions
import com.dudo.fishing.data.FishingPoint
import com.dudo.fishing.data.PointRepository
import com.dudo.fishing.data.Settings
import com.dudo.fishing.scoring.PointResult
import com.dudo.fishing.scoring.ScoreEngine
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
    private val repo = ConditionsRepository(settings)
    private val points: List<FishingPoint> = PointRepository.load(app)

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
                rank(repo.load(date))
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "알 수 없는 오류") }
            }
        }
    }

    private fun rank(c: DayConditions) {
        val sp = _state.value.species
        val results = points.map { p ->
            if (sp == null) ScoreEngine.bestForPoint(p, c) else ScoreEngine.evaluate(p, sp, c)
        }.sortedByDescending { it.best.score }
        _state.update { it.copy(loading = false, conditions = c, results = results) }
    }
}
