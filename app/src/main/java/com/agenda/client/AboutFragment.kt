package com.agenda.client

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import java.util.Date

/** Раздел «О приложении»: версия, справка и ручная проверка обновлений с понятной диагностикой. */
class AboutFragment : Fragment(R.layout.fragment_about) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.tvVersion).text = "Версия ${versionName()}"

        val host = requireActivity() as AppHost
        val tvStatus = view.findViewById<TextView>(R.id.tvUpdateStatus)
        val tvResult = view.findViewById<TextView>(R.id.tvUpdateResult)
        val btnCheck = view.findViewById<Button>(R.id.btnCheckUpdate)
        val btnDownload = view.findViewById<Button>(R.id.btnDownloadUpdate)

        fun showStatus() {
            val status = host.updateStatus
            val time = if (status.lastSuccessAt > 0) {
                DateFormat.format("d MMM, HH:mm", Date(status.lastSuccessAt)).toString()
            } else {
                null
            }
            tvStatus.text = UpdateText.status(status, time)
        }
        showStatus()

        btnCheck.setOnClickListener {
            btnCheck.isEnabled = false
            btnDownload.visibility = View.GONE
            tvResult.visibility = View.VISIBLE
            tvResult.text = "Проверяю…"

            host.checkForUpdates { result ->
                if (!isAdded || view.parent == null) return@checkForUpdates
                btnCheck.isEnabled = true
                tvResult.text = UpdateText.result(result)
                showStatus()
                if (result is UpdateCheckResult.Available) {
                    btnDownload.visibility = View.VISIBLE
                    btnDownload.setOnClickListener { host.openUpdate(result.info) }
                }
            }
        }

        // --- ССЫЛКИ ---

        // Клик по GitHub репозиторию
        view.findViewById<View>(R.id.layoutGithub).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/artiomburcovschi-max/APK"))
            startActivity(intent)
        }

        // Клик по почте поддержки
        view.findViewById<View>(R.id.layoutEmail).setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:support.watchyourgrade@gmail.com")
                putExtra(Intent.EXTRA_SUBJECT, "WatchYourGrade Feedback")
            }
            startActivity(intent)
        }
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