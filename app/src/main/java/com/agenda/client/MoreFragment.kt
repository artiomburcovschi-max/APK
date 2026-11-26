package com.agenda.client

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment

/** Вкладка «Ещё»: обычный список редких разделов (состав и порядок в NavLogic.MORE_ITEMS). */
class MoreFragment : Fragment(R.layout.fragment_more) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val host = requireActivity() as AppHost
        val container = view.findViewById<LinearLayout>(R.id.moreContainer)
        val inflater = LayoutInflater.from(requireContext())

        fun addRow(title: String, icon: Int, onClick: () -> Unit) {
            val row = inflater.inflate(R.layout.item_more_row, container, false)
            row.findViewById<ImageView>(R.id.ivMoreIcon).setImageResource(icon)
            row.findViewById<TextView>(R.id.tvMoreTitle).text = title
            row.setOnClickListener { onClick() }
            container.addView(row)
        }

        for (section in NavLogic.MORE_ITEMS) {
            addRow(section.title, iconFor(section)) { host.openSection(section) }
        }
        // Не экран, а действие: открывает системное окно «Поделиться» с короткой ссылкой на сайт
        addRow("Поделиться приложением", R.drawable.ic_share) { shareApp() }
    }

    private fun shareApp() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, ShareText.message())
        }
        startActivity(Intent.createChooser(send, "Поделиться приложением"))
    }

    private fun iconFor(section: Section): Int = when (section) {
        Section.DIARY -> R.drawable.ic_nav_diary
        Section.HOMEWORK -> R.drawable.ic_nav_homework
        Section.ABSENCES -> R.drawable.ic_nav_absences
        Section.TEACHERS -> R.drawable.ic_nav_recent // как было в шторке: отдельного значка у учителей нет
        Section.ACCOUNT -> R.drawable.ic_nav_account
        Section.SETTINGS -> R.drawable.ic_nav_settings
        else -> R.drawable.ic_nav_info
    }
}
