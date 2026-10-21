package com.agenda.client

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Экран аналитики: общий средний, средние по предметам и динамика.
 * Данные берём из кэша (там уже лежат оценки вместе с датами), в сеть не ходим.
 */
class AnalyticsActivity : AppCompatActivity() {

    private var items: List<SubjectGrades> = emptyList()
    private var semesters: List<String> = emptyList()
    private var semesterIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analytics)

        items = SnapshotCache(this).load()
        // семестры, в которых есть хотя бы одна оценка; по умолчанию показываем самый свежий
        semesters = items.filter { it.scores.isNotEmpty() }.map { it.semester }.distinct()
        semesterIndex = (semesters.size - 1).coerceAtLeast(0)

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnSemester).setOnClickListener {
            if (semesters.size > 1) {
                semesterIndex = (semesterIndex + 1) % semesters.size
                render()
            }
        }
        render()
    }

    private fun render() {
        val tvSemester = findViewById<TextView>(R.id.tvSemester)
        val tvOverall = findViewById<TextView>(R.id.tvOverall)
        val tvInfo = findViewById<TextView>(R.id.tvInfo)
        val tvChartHint = findViewById<TextView>(R.id.tvChartHint)
        val chart = findViewById<LineChartView>(R.id.chart)
        val container = findViewById<LinearLayout>(R.id.subjectsContainer)
        val btnSemester = findViewById<Button>(R.id.btnSemester)

        container.removeAllViews()

        if (semesters.isEmpty()) {
            tvSemester.text = ""
            tvOverall.text = "—"
            tvInfo.text = "Пока нет данных. Обнови оценки на главном экране."
            tvChartHint.text = ""
            chart.visibility = View.GONE
            btnSemester.visibility = View.GONE
            return
        }

        val semester = semesters[semesterIndex]
        val current = items.filter { it.semester == semester }
        tvSemester.text = semester.ifEmpty { "Семестр" }
        btnSemester.visibility = if (semesters.size > 1) View.VISIBLE else View.GONE

        // общий и по предметам
        val averages = Stats.subjectAverages(current)
        val overall = Stats.overall(averages)
        tvOverall.text = if (overall == null) "—" else String.format(Locale.US, "%.2f", overall)
        if (overall != null) tvOverall.setTextColor(GradeColors.forScore(overall))

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
        for (avg in averages.sortedByDescending { it.average }) {
            val row = layoutInflater.inflate(R.layout.item_subject_avg, container, false)
            row.findViewById<TextView>(R.id.tvAvgName).text = avg.subject
            row.findViewById<TextView>(R.id.tvAvgValue).text =
                String.format(Locale.US, "%.2f  ·  оценок: %d", avg.average, avg.count)
            val bar = row.findViewById<ProgressBar>(R.id.pbAvg)
            bar.progress = (avg.average * 10).roundToInt().coerceIn(0, 100)
            bar.progressTintList = ColorStateList.valueOf(GradeColors.forScore(avg.average))
            container.addView(row)
        }
    }
}
