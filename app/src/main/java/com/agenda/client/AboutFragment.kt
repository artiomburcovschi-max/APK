package com.agenda.client

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment

/** Раздел «О приложении»: версия и короткая справка. */
class AboutFragment : Fragment(R.layout.fragment_about) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<TextView>(R.id.tvVersion).text = "Версия ${versionName()}"
    }

    // BuildConfig в новых проектах по умолчанию не генерируется, поэтому спрашиваем у системы
    @Suppress("DEPRECATION")
    private fun versionName(): String = try {
        val ctx = requireContext()
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }
}
