package com.agenda.client

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
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

        fun render() {
            adapter.submit(h.grades)
            tvStatus.text = h.status
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
