package com.agenda.client

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment

/**
 * Раздел «Дневник»: уроки выбранного дня с учителем, домашним заданием и оценкой.
 * Данные берутся из календаря на главной странице портала (см. AgendaParser) и копятся по мере обновлений.
 * Листаются только дни, по которым есть уроки.
 */
class DiaryFragment : Fragment(R.layout.fragment_diary) {

    private var host: AppHost? = null
    private var selected: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val h = requireActivity() as AppHost
        host = h
        selected = savedInstanceState?.getString(KEY_DATE)

        val tvTitle = view.findViewById<TextView>(R.id.tvDayTitle)
        val tvSummary = view.findViewById<TextView>(R.id.tvDaySummary)
        val tvEmpty = view.findViewById<TextView>(R.id.tvDiaryEmpty)
        val btnPrev = view.findViewById<Button>(R.id.btnPrevDay)
        val btnNext = view.findViewById<Button>(R.id.btnNextDay)
        val btnToday = view.findViewById<Button>(R.id.btnToday)
        val container = view.findViewById<LinearLayout>(R.id.lessonContainer)

        fun render() {
            val lessons = h.extras.lessons
            val dates = DiaryLogic.dates(lessons)
            container.removeAllViews()

            if (dates.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
                tvTitle.text = "Дневник"
                tvSummary.text = ""
                btnPrev.isEnabled = false
                btnNext.isEnabled = false
                btnToday.visibility = View.GONE
                return
            }
            tvEmpty.visibility = View.GONE

            val nearest = DiaryLogic.initialDate(dates, DiaryLogic.todayIso())!!
            // выбранный день мог исчезнуть из памяти (очень старый): тогда открываем ближайший
            val current = selected?.takeIf { it in dates } ?: nearest
            selected = current
            val index = dates.indexOf(current)

            val day = lessons.filter { it.date == current }.sortedBy { it.order }
            tvTitle.text = DiaryLogic.dayTitle(current)
            tvSummary.text = DiaryLogic.summary(day)
            btnPrev.isEnabled = index > 0
            btnNext.isEnabled = index < dates.size - 1
            btnToday.visibility = if (current != nearest) View.VISIBLE else View.GONE

            val inflater = LayoutInflater.from(requireContext())
            for (lesson in day) {
                val row = inflater.inflate(R.layout.item_lesson, container, false)
                row.findViewById<TextView>(R.id.tvLessonOrder).text = lesson.order.toString()
                row.findViewById<TextView>(R.id.tvLessonTime).text =
                    if (lesson.start.isNotEmpty()) lesson.start + "\n" + lesson.end else ""
                row.findViewById<TextView>(R.id.tvLessonSubject).text = lesson.subject
                row.findViewById<TextView>(R.id.tvLessonTeacher).apply {
                    text = lesson.teacher
                    visibility = if (lesson.teacher.isEmpty()) View.GONE else View.VISIBLE
                }
                row.findViewById<TextView>(R.id.tvLessonHomework).apply {
                    text = "ДЗ: " + lesson.homework
                    visibility = if (lesson.homework.isEmpty()) View.GONE else View.VISIBLE
                }
                row.findViewById<TextView>(R.id.tvLessonGrade).apply {
                    if (lesson.grades.isEmpty()) {
                        visibility = View.GONE
                    } else {
                        visibility = View.VISIBLE
                        text = lesson.grades.joinToString(" ")
                        setTextColor(GradeColors.forScore(context, lesson.grades.average()))
                    }
                }
                container.addView(row)
            }
        }

        btnPrev.setOnClickListener { move(-1, h); render() }
        btnNext.setOnClickListener { move(+1, h); render() }
        btnToday.setOnClickListener { selected = null; render() }
        render()

        // Когда обновление закончилось, в памяти могли появиться новые дни
        ScreenRefresh.listen(h) { render() }
    }

    private fun move(step: Int, h: AppHost) {
        val dates = DiaryLogic.dates(h.extras.lessons)
        val index = dates.indexOf(selected)
        if (index >= 0) selected = dates.getOrNull(index + step) ?: selected
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_DATE, selected)
    }

    override fun onDestroyView() {
        host?.setStateListener(null)
        host = null
        super.onDestroyView()
    }

    private companion object {
        const val KEY_DATE = "diary_date"
    }
}
