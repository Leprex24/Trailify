package com.trailify.feature.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailify.core.domain.model.ActivitySummary
import com.trailify.core.domain.model.PoiCollectionEntry
import com.trailify.core.domain.model.Profile
import com.trailify.core.domain.repository.ActivityRepository
import com.trailify.core.domain.repository.PoiRepository
import com.trailify.core.domain.repository.ProfileRepository
import com.trailify.core.domain.stats.MonthStats
import com.trailify.core.domain.stats.StatMetric
import com.trailify.core.domain.stats.buildMonthStats
import com.trailify.core.domain.stats.monthBounds
import com.trailify.core.ui.UiState
import com.trailify.core.ui.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class ProfileUiState(
    val profile: UiState<Profile> = UiState.Loading,
    /** Zebrane ciekawe punkty (W24), od najnowszego. */
    val collection: UiState<List<PoiCollectionEntry>> = UiState.Loading,
    /** Historia aktywności (W25), od najnowszej. */
    val activities: UiState<List<ActivitySummary>> = UiState.Loading,
    /** Miesiąc na zakładce Statystyki i jego dane. */
    val month: YearMonth = YearMonth.now(),
    val monthStats: UiState<MonthStats> = UiState.Loading,
    val metric: StatMetric = StatMetric.DISTANCE,
    /** Dzień wybrany na wykresie (szczegóły aktywności pod wykresem). */
    val selectedDay: LocalDate? = null,
)

/** Profil (W23-W25): dane i statystyki, zebrane punkty, historia aktywności i statystyki miesięczne. */
class ProfileViewModel(
    private val profiles: ProfileRepository,
    private val pois: PoiRepository,
    private val activities: ActivityRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState(month = YearMonth.from(today())))
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    private var monthJob: Job? = null

    /** Wołane przez ProfileScreen przy każdym wejściu na ekran. Zapytania idą równolegle. */
    fun load() {
        viewModelScope.launch {
            if (_state.value.profile !is UiState.Success) _state.update { it.copy(profile = UiState.Loading) }
            val profile = async { result { profiles.getMe() } }
            val collection = async { result { pois.myCollection() } }
            val history = async { result { activities.getMine(page = 0, size = HISTORY_SIZE).items } }
            _state.update { it.copy(profile = profile.await(), collection = collection.await(), activities = history.await()) }
        }
        loadMonth()
    }

    fun previousMonth() = changeMonth(_state.value.month.minusMonths(1))

    /** Przyszłych miesięcy nie ma sensu oglądać. */
    fun nextMonth() {
        val next = _state.value.month.plusMonths(1)
        if (!next.isAfter(YearMonth.from(today()))) changeMonth(next)
    }

    fun selectMetric(metric: StatMetric) = _state.update { it.copy(metric = metric) }

    /** Dotknięcie dnia na wykresie wybiera go, ponowne dotknięcie odznacza. */
    fun selectDay(day: LocalDate) = _state.update { it.copy(selectedDay = if (it.selectedDay == day) null else day) }

    private fun changeMonth(month: YearMonth) {
        _state.update { it.copy(month = month, selectedDay = null, monthStats = UiState.Loading) }
        loadMonth()
    }

    private fun loadMonth() {
        monthJob?.cancel() // szybkie klikanie strzałek: liczy się tylko ostatnio wybrany miesiąc
        val month = _state.value.month
        monthJob = viewModelScope.launch {
            val result = result { buildMonthStats(fetchMonth(month), month, zone) }
            _state.update { if (it.month == month) it.copy(monthStats = result) else it }
        }
    }

    /** Wszystkie aktywności miesiąca (kilka stron, gdyby było ich więcej niż [MONTH_PAGE_SIZE]). */
    private suspend fun fetchMonth(month: YearMonth): List<ActivitySummary> {
        val (from, to) = monthBounds(month, zone)
        val all = mutableListOf<ActivitySummary>()
        var page = 0
        while (true) {
            val chunk = activities.getMine(page = page, size = MONTH_PAGE_SIZE, from = from, to = to)
            all += chunk.items
            page++
            if (page >= chunk.totalPages || page >= MONTH_MAX_PAGES) return all
        }
    }

    private suspend fun <T> result(block: suspend () -> T): UiState<T> = try {
        UiState.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("Profile", "Pobieranie danych profilu nie powiodło się", e)
        UiState.Error(e.toUserMessage())
    }

    internal companion object {
        /** Tyle ostatnich aktywności pokazujemy w profilu. */
        const val HISTORY_SIZE = 30
        const val MONTH_PAGE_SIZE = 100
        const val MONTH_MAX_PAGES = 5
    }
}
