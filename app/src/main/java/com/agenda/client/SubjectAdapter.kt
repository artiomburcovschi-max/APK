package com.agenda.client

import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class SubjectAdapter : RecyclerView.Adapter<SubjectAdapter.VH>() {

    private val items = mutableListOf<SubjectGrades>()

    fun submit(newItems: List<SubjectGrades>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvSubject: TextView = view.findViewById(R.id.tvSubject)
        val tvAverage: TextView = view.findViewById(R.id.tvAverage)
        val tvScores: TextView = view.findViewById(R.id.tvScores)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_subject, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvSubject.text = item.subject

        // подпись под названием: семестр · среднее · официальная оценка
        val parts = mutableListOf<String>()
        if (item.semester.isNotEmpty()) parts.add(item.semester)
        if (item.scores.isNotEmpty()) {
            parts.add(String.format(Locale.US, "среднее: %.1f", item.scores.average()))
        } else {
            parts.add("нет оценок")
        }
        if (item.media.isNotEmpty()) parts.add(item.media)
        holder.tvAverage.text = parts.joinToString(" · ")

        if (item.scores.isEmpty()) {
            holder.tvScores.text = "—"
            holder.tvScores.setTextColor(Color.parseColor("#9E9E9E"))
            return
        }

        // каждая оценка своим цветом
        val sb = SpannableStringBuilder()
        item.scores.forEachIndexed { i, score ->
            if (i > 0) sb.append("  ")
            val start = sb.length
            sb.append(score.toString())
            sb.setSpan(
                ForegroundColorSpan(colorFor(score)),
                start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        holder.tvScores.text = sb
    }

    private fun colorFor(score: Int): Int = when {
        score >= 9 -> Color.parseColor("#2E7D32") // зелёный
        score >= 7 -> Color.parseColor("#F9A825") // жёлтый
        score >= 5 -> Color.parseColor("#EF6C00") // оранжевый
        else -> Color.parseColor("#C62828")       // красный
    }
}