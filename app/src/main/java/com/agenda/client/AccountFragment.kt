package com.agenda.client

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment

/** Раздел «Аккаунт»: кто вошёл и выход со стиранием данных (само стирание делает MainActivity). */
class AccountFragment : Fragment(R.layout.fragment_account) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val host = requireActivity() as AppHost
        view.findViewById<TextView>(R.id.tvAccountLogin).text = host.username
        view.findViewById<TextView>(R.id.tvAccountType).text = "Тип входа: ${host.accountType.label}"
        view.findViewById<Button>(R.id.btnLogout).setOnClickListener { host.confirmLogout() }
    }
}
