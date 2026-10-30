package com.agenda.client

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment

class AccountFragment : Fragment(R.layout.fragment_account) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val host = requireActivity() as AppHost
        view.findViewById<TextView>(R.id.tvAccountLogin).text = host.username
        view.findViewById<TextView>(R.id.tvAccountType).text = "Тип входа: ${host.accountType.label}"

        val dosar = host.extras.dosar
        val lines = listOfNotNull(
            dosar?.className?.let { "Класс: $it" },
            dosar?.institution?.let { "Учебное заведение: $it" },
            dosar?.formTeacher?.let { "Классный руководитель: $it" }
        )
        view.findViewById<TextView>(R.id.tvAccountDosar).apply {
            text = lines.joinToString("\n")
            visibility = if (lines.isEmpty()) View.GONE else View.VISIBLE
        }
        view.findViewById<Button>(R.id.btnLogout).setOnClickListener { host.confirmLogout() }
    }
}
