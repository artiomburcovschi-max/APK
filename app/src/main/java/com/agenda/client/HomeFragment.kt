package com.agenda.client

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.util.TimeZone

/**
 * Вкладка «Главная»: что сейчас (урок или перемена), новые оценки и домашка на ближайшие дни.
 * Что показывать, решает HomeLogic (под тестами); здесь отрисовка и секундный тик карточки «сейчас»,
 * который работает, пока экран на виду.
 */
class HomeFragment : Fragment(R.layout.fragment_home) {

    private class Views(root: View) {
        val status: TextView = root.findViewById(R.id.tvHomeStatus)
        val refresh: Button = root.findViewById(R.id.btnHomeRefresh)
        val progressPanel: View = root.findViewById(R.id.homeProgressPanel)
        val progressAction: TextView = root.findViewById(R.id.tvHomeProgressAction)
        val progressHide: Button = root.findViewById(R.id.btnHomeProgressHide)
        val progressBar: LinearProgressIndicator = root.findViewById(R.id.homeProgressBar)
        val liveCard: View = root.findViewById(R.id.cardHomeLive)
        val liveTitle: TextView = root.findViewById(R.id.tvHomeLiveTitle)
        val countdown: TextView = root.findViewById(R.id.tvHomeCountdown)
        val caption: TextView = root.findViewById(R.id.tvHomeLiveCaption)
        val next: TextView = root.findViewById(R.id.tvHomeLiveNext)
        val recentEmpty: TextView = root.findViewById(R.id.tvHomeRecentEmpty)
        val recentContainer: LinearLayout = root.findViewById(R.id.homeRecentContainer)
        val allRecent: Button = root.findViewById(R.id.btnHomeAllRecent)
        val hwEmpty: TextView = root.findViewById(R.id.tvHomeHwEmpty)
        val hwContainer: LinearLayout = root.findViewById(R.id.homeHwContainer)
        val allHw: Button = root.findViewById(R.id.btnHomeAllHw)
    }

    private var host: AppHost? = null
    private var ui: Views? = null
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            updateLive()
            // следующий тик на границе секунды: цифры меняются ровно
            handler.postDelayed(this, 1000L - System.currentTimeMillis() % 1000L + 15L)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val h = requireActivity() as AppHost
        host = h
        val views = Views(view)
        ui = views

        views.refresh.setOnClickListener { h.requestRefresh() }
        views.progressHide.setOnClickListener { h.hideProgress() }
        views.liveCard.setOnClickListener { h.openSection(Section.SCHEDULE) }
        views.allRecent.setOnClickListener { h.openSection(Section.RECENT) }
        views.allHw.setOnClickListener { h.openSection(Section.HOMEWORK) }

        renderStatus()
        renderHomework()
        loadRecent()
        updateLive()

        // Слушатель в AppHost один на всё приложение: снимаем его в onDestroyView.
        // Статус перерисовываем на каждый тик обновления, остальное только когда обновление закончилось.
        var wasLoading = h.progress != null
        h.setStateListener {
            val loading = h.progress != null
            renderStatus()
            if (wasLoading && !loading) {
                renderHomework()
                loadRecent()
                updateLive()
            }
            wasLoading = loading
        }
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        // пока экрана не было, данные могли обновиться, а день смениться
        renderStatus()
        renderHomework()
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    // ---------- статус ----------

    private fun renderStatus() {
        val h = host ?: return
        val views = ui ?: return
        val progress = h.progress
        views.status.text = h.status
        views.refresh.isEnabled = progress == null

        // Панель видна, пока идёт обновление и человек её не скрыл (строка статуса с процентом остаётся всегда)
        if (progress != null && !h.progressHidden) {
            views.progressPanel.visibility = View.VISIBLE
            views.progressAction.text = "Сейчас: ${progress.stage.label}"
            views.progressBar.setProgressCompat(progress.percent, true)
        } else {
            views.progressPanel.visibility = View.GONE
        }
    }

    // ---------- что сейчас ----------

    private fun updateLive() {
        val h = host ?: return
        val views = ui ?: return
        val now = System.currentTimeMillis()
        val zone = TimeZone.getDefault()

        val state = LessonClock.stateAt(h.extras.lessons, now, zone)
        val info = LessonClock.info(state, now, zone)

        views.liveTitle.text = info.title

        val countdown = info.countdownMs
        if (countdown == null) {
            views.countdown.visibility = View.GONE
        } else {
            views.countdown.visibility = View.VISIBLE
            views.countdown.text = LessonClock.formatRemaining(countdown)
            // перемена зелёная, урок синий: как в «Расписании»
            val colorRes = if (state is ClockState.Break) R.color.grade_excellent else R.color.wyg_primary
            views.countdown.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
        }

        views.caption.text = info.caption
        views.caption.visibility = if (info.caption.isEmpty()) View.GONE else View.VISIBLE
        views.next.text = info.nextLine.orEmpty()
        views.next.visibility = if (info.nextLine == null) View.GONE else View.VISIBLE
    }

    // ---------- новые оценки ----------

    private fun loadRecent() {
        val h = host ?: return
        // База читается не в главном потоке: запись очередного обновления может её ненадолго занять
        Thread {
            val items = try { HomeLogic.recentToShow(h.recentGrades()) } catch (e: Exception) { emptyList() }
            activity?.runOnUiThread {
                if (isAdded && ui != null) renderRecent(items)
            }
        }.start()
    }

    private fun renderRecent(items: List<FeedItem>) {
        val views = ui ?: return
        views.recentContainer.removeAllViews()
        views.recentEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        val inflater = LayoutInflater.from(requireContext())
        for (item in items) {
            val row = inflater.inflate(R.layout.item_recent, views.recentContainer, false)
            row.findViewById<TextView>(R.id.tvRecentScore).apply {
                text = item.score.toString()
                val pill = if (Descriptors.isDescriptorScore(item.subject, item.score)) {
                    ContextCompat.getColor(context, R.color.wyg_text_secondary) // не оценка 1–10: без цвета оценки
                } else {
                    GradeColors.forScore(context, item.score.toDouble())
                }
                UiStyle.tintPill(this, pill)
            }
            row.findViewById<TextView>(R.id.tvRecentSubject).text = item.subject
            row.findViewById<TextView>(R.id.tvRecentWhen).text = RecentFeed.subtitle(item)
            views.recentContainer.addView(row)
        }
    }

    // ---------- домашка ----------

    private fun renderHomework() {
        val h = host ?: return
        val views = ui ?: return
        val items = HomeLogic.homeworkSoon(h.extras, DiaryLogic.todayIso())
        views.hwContainer.removeAllViews()
        views.hwEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        val inflater = LayoutInflater.from(requireContext())
        for (item in items) {
            val row = inflater.inflate(R.layout.item_home_hw, views.hwContainer, false)
            row.findViewById<TextView>(R.id.tvHomeHwWhen).text = item.dayTitle + " · " + item.subject
            row.findViewById<TextView>(R.id.tvHomeHwText).text = item.text
            views.hwContainer.addView(row)
        }
    }

    override fun onDestroyView() {
        handler.removeCallbacks(ticker)
        host?.setStateListener(null) // слушатель в AppHost один на всё приложение: снимаем за собой
        host = null
        ui = null
        super.onDestroyView()
    }
}
