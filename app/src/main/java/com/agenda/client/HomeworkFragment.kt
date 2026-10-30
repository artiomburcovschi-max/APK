package com.agenda.client

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class HomeworkFragment : Fragment(R.layout.fragment_homework) {

    private var host: AppHost? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val h = requireActivity() as AppHost
        host = h

        val container = view.findViewById<LinearLayout>(R.id.homeworkContainer)
        val tvEmpty = view.findViewById<TextView>(R.id.tvHomeworkEmpty)

        fun render() {
            container.removeAllViews()
            val rows = HomeworkLogic.rows(h.extras, DiaryLogic.todayIso())
            tvEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE

            val inflater = LayoutInflater.from(requireContext())
            for (row in rows) {
                when (row) {
                    is HwRow.Section -> container.addView(
                        (inflater.inflate(R.layout.item_hw_header, container, false) as TextView).apply {
                            text = row.title
                            textSize = 20f
                            setTextColor(ContextCompat.getColor(context, R.color.wyg_text_primary))
                            setPadding(0, dp(if (container.childCount == 0) 4 else 20), 0, 0)
                        }
                    )
                    is HwRow.DateHeader -> container.addView(
                        (inflater.inflate(R.layout.item_hw_header, container, false) as TextView).apply {
                            text = row.title
                            textSize = 14f
                            setTextColor(ContextCompat.getColor(context, R.color.wyg_text_secondary))
                            setPadding(0, dp(12), 0, 0)
                        }
                    )
                    is HwRow.Item -> container.addView(
                        inflater.inflate(R.layout.item_hw_item, container, false).also {
                            it.findViewById<TextView>(R.id.tvHwSubject).text = row.subject
                            it.findViewById<TextView>(R.id.tvHwText).text = row.text
                        }
                    )
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
