package com.agenda.client

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayout
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Раздел «Аналитика»: две вкладки.
 *  «Обзор»: общий средний, средние по предметам и динамика.
 *  «Цель»: что нужно получить по предмету, чтобы дойти до целевого среднего (экран в GoalTab).
 * Данные берём из кэша (там уже лежат оценки вместе с датами), в сеть не ходим.
 * Семестр выбирается один раз сверху и действует на обе вкладки.
 */
class AnalyticsFragment : Fragment(R.layout.fragment_analytics) {

    private var items: List<SubjectGrades> = emptyList()
    private var semesters: List<String> = emptyList()
    private var semesterIndex = 0
    private var tabIndex = 0
    private lateinit var goalTab: GoalTab

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        items = SnapshotCache(requireContext()).load()
        goalTab = GoalTab(view, GoalStore(requireContext()))
        setupTabs(view, savedInstanceState?.getInt(KEY_TAB) ?: 0)
        // семестры, в которых есть хотя бы одна оценка; по умолчанию показываем самый свежий
        semesters = items.filter { it.scores.isNotEmpty() }.map { it.semester }.distinct()
        val last = (semesters.size - 1).coerceAtLeast(0)
        semesterIndex = (savedInstanceState?.getInt(KEY_SEMESTER) ?: last).coerceIn(0, last)

        view.findViewById<Button>(R.id.btnSemester).setOnClickListener {
            if (semesters.size > 1) {
                semesterIndex = (semesterIndex + 1) % semesters.size
                render(view)
            }
        }
        render(view)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SEMESTER, semesterIndex)
        outState.putInt(KEY_TAB, tabIndex)
    }

    private fun setupTabs(root: View, initial: Int) {
        val tabs = root.findViewById<TabLayout>(R.id.tabs)
        tabIndex = initial.coerceIn(0, 1)
        showTab(root)
        // Сначала выбираем вкладку, и только потом вешаем слушатель: иначе выбор вызовет его сам
        tabs.getTabAt(tabIndex)?.select()
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tabIndex = tab?.position ?: return
                showTab(root)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun showTab(root: View) {
        root.findViewById<View>(R.id.overviewContent).visibility =
            if (tabIndex == 0) View.VISIBLE else View.GONE
        root.findViewById<View>(R.id.goalContent).visibility =
            if (tabIndex == 1) View.VISIBLE else View.GONE
    }

    private fun render(root: View) {
        val tvSemester = root.findViewById<TextView>(R.id.tvSemester)
        val tvOverall = root.findViewById<TextView>(R.id.tvOverall)
        val tvInfo = root.findViewById<TextView>(R.id.tvInfo)
        val tvChartHint = root.findViewById<TextView>(R.id.tvChartHint)
        val chart = root.findViewById<LineChartView>(R.id.chart)
        val container = root.findViewById<LinearLayout>(R.id.subjectsContainer)
        val btnSemester = root.findViewById<Button>(R.id.btnSemester)

        container.removeAllViews()

        if (semesters.isEmpty()) {
            tvSemester.text = ""
            tvOverall.text = "—"
            tvInfo.text = "Пока нет данных. Обнови оценки в разделе «Оценки»."
            tvChartHint.text = ""
            chart.visibility = View.GONE
            btnSemester.visibility = View.GONE
            goalTab.bind("", emptyList(), emptyList())
            return
        }

        val semester = semesters[semesterIndex]
        val current = items.filter { it.semester == semester }
        goalTab.bind(semester, current, items)
        tvSemester.text = semester.ifEmpty { "Семестр" }
        btnSemester.visibility = if (semesters.size > 1) View.VISIBLE else View.GONE

        // общий и по предметам
        val averages = Stats.subjectAverages(current)
        val overall = Stats.overall(averages)
        tvOverall.text = if (overall == null) "—" else String.format(Locale.US, "%.2f", overall)
        if (overall != null) tvOverall.setTextColor(GradeColors.forScore(requireContext(), overall))

        // динамика: только по оценкам, у которых известна дата
        val totalGrades = current.sumOf { it.scores.size }
        val dated = Stats.datedGrades(current)
        tvInfo.text = "Оценок с известной датой: ${dated.size} из $totalGrades"

        val trend = Stats.runningOverall(dated)
        if (trend.size >= 2) {
            chart.visibility = View.VISIBLE
            chart.setData(
                trend.map { it.value.toFloat() },
                trend.map { GradeDates.toShort(it.date) ?: "" }
            )
            tvChartHint.text = "Динамика общего среднего (по оценкам с датой)"
        } else {
            chart.visibility = View.GONE
            tvChartHint.text = "График появится, когда накопится минимум 2 оценки с датой"
        }

        // строки по предметам: сначала самые сильные
        val inflater = LayoutInflater.from(requireContext())
        for (avg in averages.sortedByDescending { it.average }) {
            val row = inflater.inflate(R.layout.item_subject_avg, container, false)
            row.findViewById<TextView>(R.id.tvAvgName).text = avg.subject
            row.findViewById<TextView>(R.id.tvAvgValue).text =
                String.format(Locale.US, "%.2f  ·  оценок: %d", avg.average, avg.count)
            val bar = row.findViewById<ProgressBar>(R.id.pbAvg)
            bar.progress = (avg.average * 10).roundToInt().coerceIn(0, 100)
            bar.progressTintList = ColorStateList.valueOf(GradeColors.forScore(requireContext(), avg.average))
            container.addView(row)
        }
    }

    private companion object {
        const val KEY_SEMESTER = "semester_index"
        const val KEY_TAB = "tab_index"
    }
}
