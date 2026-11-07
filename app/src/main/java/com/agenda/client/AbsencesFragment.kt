package com.agenda.client

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

/** Раздел «Пропуски»: сводка по семестрам, пропуски по предметам и по дням. Тексты собирает AbsenceScreens. */
class AbsencesFragment : Fragment(R.layout.fragment_absences) {

    private var host: AppHost? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val h = requireActivity() as AppHost
        host = h

        val content = view.findViewById<View>(R.id.absencesContent)
        val tvEmpty = view.findViewById<TextView>(R.id.tvAbsencesEmpty)
        val tvTotal = view.findViewById<TextView>(R.id.tvAbsTotal)
        val tvSemesters = view.findViewById<TextView>(R.id.tvAbsSemesters)
        val tvPresence = view.findViewById<TextView>(R.id.tvAbsPresence)
        val tvNote = view.findViewById<TextView>(R.id.tvAbsNote)
        val subjectsTitle = view.findViewById<View>(R.id.tvAbsSubjectsTitle)
        val subjects = view.findViewById<LinearLayout>(R.id.absSubjectsContainer)
        val daysTitle = view.findViewById<View>(R.id.tvAbsDaysTitle)
        val days = view.findViewById<LinearLayout>(R.id.absDaysContainer)

        fun render() {
            val screen = AbsenceScreens.build(h.extras.absences)
            subjects.removeAllViews()
            days.removeAllViews()
            if (screen == null) {
                content.visibility = View.GONE
                tvEmpty.visibility = View.VISIBLE
                return
            }
            content.visibility = View.VISIBLE
            tvEmpty.visibility = View.GONE

            tvTotal.text = screen.totalLine
            tvSemesters.text = screen.semesterLines.joinToString("\n")
            tvPresence.apply {
                text = screen.presenceLine.orEmpty()
                visibility = if (screen.presenceLine == null) View.GONE else View.VISIBLE
                // посещаемость красим по привычной шкале: от 90 хорошо, от 75 так себе, ниже плохо
                val percent = h.extras.absences?.presencePercent ?: 100
                setTextColor(
                    ContextCompat.getColor(
                        context,
                        when {
                            percent >= 90 -> R.color.grade_excellent
                            percent >= 75 -> R.color.grade_mid
                            else -> R.color.grade_bad
                        }
                    )
                )
            }
            tvNote.apply {
                text = screen.listNote.orEmpty()
                visibility = if (screen.listNote == null) View.GONE else View.VISIBLE
            }

            val inflater = LayoutInflater.from(requireContext())

            subjectsTitle.visibility = if (screen.subjects.isEmpty()) View.GONE else View.VISIBLE
            // самая длинная полоска это предмет с наибольшим числом пропусков, остальные в той же шкале
            val maxTotal = screen.subjects.maxOfOrNull { it.semester1.total + it.semester2.total }?.coerceAtLeast(1) ?: 1
            for (item in screen.subjects) {
                val total = item.semester1.total + item.semester2.total
                val motivated = item.semester1.motivated + item.semester2.motivated
                val row = inflater.inflate(R.layout.item_abs_subject, subjects, false)
                row.findViewById<TextView>(R.id.tvAbsSubject).text = item.subject
                row.findViewById<TextView>(R.id.tvAbsCount).text =
                    if (motivated > 0) "$total · мотив. $motivated" else total.toString()
                val track = row.findViewById<LinearLayout>(R.id.absBarTrack)
                val fill = row.findViewById<View>(R.id.absBarFill)
                track.weightSum = maxTotal.toFloat()
                val fillParams = fill.layoutParams as LinearLayout.LayoutParams
                fillParams.weight = total.toFloat()
                fill.layoutParams = fillParams
                subjects.addView(row)
            }

            daysTitle.visibility = if (screen.days.isEmpty()) View.GONE else View.VISIBLE
            for (day in screen.days) {
                // день одной карточкой: дата сверху, пропущенные уроки списком внутри
                val card = inflater.inflate(R.layout.item_abs_day, days, false)
                card.findViewById<TextView>(R.id.tvAbsDayTitle).text = day.title
                val entriesBox = card.findViewById<LinearLayout>(R.id.absDayEntries)
                days.addView(card)
                for (entry in day.entries) {
                    val row = inflater.inflate(R.layout.item_abs_entry, entriesBox, false)
                    row.findViewById<TextView>(R.id.tvEntryLesson).text = entry.lessonNo.toString()
                    row.findViewById<TextView>(R.id.tvEntrySubject).text = entry.subject
                    row.findViewById<TextView>(R.id.tvEntryStatus).apply {
                        text = AbsenceScreens.statusLabel(entry.status)
                        UiStyle.tintPill(
                            this,
                            ContextCompat.getColor(
                                context,
                                when (entry.status) {
                                    AbsenceStatus.MOTIVATED -> R.color.grade_excellent
                                    AbsenceStatus.UNMOTIVATED -> R.color.grade_bad
                                    AbsenceStatus.UNKNOWN -> R.color.wyg_text_secondary
                                }
                            )
                        )
                    }
                    entriesBox.addView(row)
                }
            }
        }
        render()
        ScreenRefresh.listen(h) { render() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        host?.setStateListener(null)
        host = null
        super.onDestroyView()
    }
}
