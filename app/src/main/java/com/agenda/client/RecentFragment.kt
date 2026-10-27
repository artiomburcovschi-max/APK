package com.agenda.client

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment

/**
 * Раздел «Новые оценки»: лента того, что приложение заметило при обновлениях (см. RecentFeed).
 * Читает базу в фоновом потоке и перечитывает её, когда очередное обновление закончилось.
 */
class RecentFragment : Fragment(R.layout.fragment_recent) {

    private var host: AppHost? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val h = requireActivity() as AppHost
        host = h

        val container = view.findViewById<LinearLayout>(R.id.recentContainer)
        val tvEmpty = view.findViewById<TextView>(R.id.tvRecentEmpty)

        fun show(items: List<FeedItem>) {
            container.removeAllViews()
            if (items.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
                tvEmpty.text = "Пока новых оценок нет. Когда приложение при обновлении заметит новую оценку, она появится здесь."
                return
            }
            tvEmpty.visibility = View.GONE
            val inflater = LayoutInflater.from(requireContext())
            for (item in items) {
                val row = inflater.inflate(R.layout.item_recent, container, false)
                row.findViewById<TextView>(R.id.tvRecentScore).apply {
                    text = item.score.toString()
                    setTextColor(GradeColors.forScore(context, item.score.toDouble()))
                }
                row.findViewById<TextView>(R.id.tvRecentSubject).text = item.subject
                row.findViewById<TextView>(R.id.tvRecentWhen).text = RecentFeed.subtitle(item)
                container.addView(row)
            }
        }

        fun load() {
            // База читается не в главном потоке: запись очередного обновления может её ненадолго занять
            Thread {
                val items = try { h.recentGrades() } catch (e: Exception) { emptyList() }
                activity?.runOnUiThread {
                    if (isAdded && view.parent != null) show(items)
                }
            }.start()
        }
        load()

        // Когда обновление закончилось, ленту перечитываем: возможно, появились новые оценки
        var wasLoading = h.progress != null
        h.setStateListener {
            val loading = h.progress != null
            if (wasLoading && !loading) load()
            wasLoading = loading
        }
    }

    override fun onDestroyView() {
        host?.setStateListener(null)
        host = null
        super.onDestroyView()
    }
}
