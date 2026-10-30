package com.agenda.client

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment

/**
 * Раздел «Учителя»: класс, классный руководитель и учителя по предметам.
 * Источник: host.extras.dosar (его заполняет DosarParser при каждом обновлении). Свой парсинг не нужен.
 */
class TeachersFragment : Fragment(R.layout.fragment_teachers) {

    private var host: AppHost? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val h = requireActivity() as AppHost
        host = h

        val container = view.findViewById<LinearLayout>(R.id.teachersContainer)
        val tvInfo = view.findViewById<TextView>(R.id.tvTeachersInfo)
        val tvEmpty = view.findViewById<TextView>(R.id.tvTeachersEmpty)

        fun render() {
            container.removeAllViews()
            val dosar = h.extras.dosar

            val info = listOfNotNull(
                dosar?.className?.let { "Класс: $it" },
                dosar?.formTeacher?.let { "Классный руководитель: $it" }
            ).joinToString("\n")
            tvInfo.text = info
            tvInfo.visibility = if (info.isEmpty()) View.GONE else View.VISIBLE

            val teachers = dosar?.teachers.orEmpty()
            tvEmpty.visibility = if (teachers.isEmpty()) View.VISIBLE else View.GONE

            // Порядок как в «Dosar»; у предмета может быть несколько учителей (например, у истории)
            val bySubject = teachers.groupBy({ it.subject }, { it.teacher }).mapValues { it.value.distinct() }
            val inflater = LayoutInflater.from(requireContext())
            for ((subject, names) in bySubject) {
                container.addView(
                    inflater.inflate(R.layout.item_hw_item, container, false).also {
                        it.findViewById<TextView>(R.id.tvHwSubject).text = subject
                        it.findViewById<TextView>(R.id.tvHwText).text = names.joinToString("\n")
                    }
                )
            }
        }
        render()
        ScreenRefresh.listen(h) { render() }
    }

    override fun onDestroyView() {
        host?.setStateListener(null)
        host = null
        super.onDestroyView()
    }
}
