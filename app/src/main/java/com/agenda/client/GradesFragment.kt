package com.agenda.client

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.google.android.material.progressindicator.LinearProgressIndicator
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Раздел «Оценки»: список предметов и кнопка «Обновить». Данные берёт у MainActivity. */
class GradesFragment : Fragment(R.layout.fragment_grades) {

    private val adapter = SubjectAdapter()
    private var host: AppHost? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val h = requireActivity() as AppHost
        host = h

        val rv = view.findViewById<RecyclerView>(R.id.rvGrades)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        val tvStatus = view.findViewById<TextView>(R.id.tvStatus)
        view.findViewById<Button>(R.id.btnRefresh).setOnClickListener { h.requestRefresh() }

        val panel = view.findViewById<View>(R.id.progressPanel)
        val tvAction = view.findViewById<TextView>(R.id.tvProgressAction)
        val bar = view.findViewById<LinearProgressIndicator>(R.id.progressBar)
        view.findViewById<Button>(R.id.btnProgressHide).setOnClickListener { h.hideProgress() }

        fun render() {
            adapter.submit(h.grades)
            tvStatus.text = h.status

            // Панель видна, пока идёт обновление и человек её не скрыл (строка статуса с процентом остаётся всегда)
            val progress = h.progress
            if (progress != null && !h.progressHidden) {
                panel.visibility = View.VISIBLE
                tvAction.text = "Сейчас: ${progress.stage.label}"
                bar.setProgressCompat(progress.percent, true)
            } else {
                panel.visibility = View.GONE
            }
        }
        h.setStateListener { render() }
        render()
    }

    override fun onDestroyView() {
        // Отписываемся, иначе Activity держит ссылку на мёртвый экран
        host?.setStateListener(null)
        host = null
        super.onDestroyView()
    }
}
