package com.agenda.client

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class SubjectAdapter : RecyclerView.Adapter<SubjectAdapter.VH>() {

    private val items = mutableListOf<SubjectGrades>()
    private val expanded = mutableSetOf<String>() // какие карточки раскрыты (по ключу семестр|предмет)

    fun submit(newItems: List<SubjectGrades>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvSubject: TextView = view.findViewById(R.id.tvSubject)
        val tvAverage: TextView = view.findViewById(R.id.tvAverage)
        val tvScores: TextView = view.findViewById(R.id.tvScores)
        val tvDetails: TextView = view.findViewById(R.id.tvDetails)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_subject, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvSubject.text = item.subject

        // подпись под названием: семестр · среднее · официальная оценка · дата последней оценки
        val parts = mutableListOf<String>()
        if (item.semester.isNotEmpty()) parts.add(item.semester)
        if (item.scores.isNotEmpty()) {
            parts.add(String.format(Locale.US, "среднее: %.2f", item.scores.average()))
        } else {
            parts.add("нет оценок")
        }
        if (item.media.isNotEmpty()) parts.add(item.media)
        val lastDate = item.dates.lastOrNull()?.let { GradeDates.toDisplay(it) }
        if (lastDate != null) parts.add("последняя: $lastDate")
        holder.tvAverage.text = parts.joinToString(" · ")

        if (item.scores.isEmpty()) {
            holder.tvScores.text = "—"
            holder.tvScores.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.wyg_text_hint))
            holder.tvDetails.visibility = View.GONE
            holder.itemView.setOnClickListener(null)
            return
        }

        // каждая оценка своим цветом
        val sb = SpannableStringBuilder()
        item.scores.forEachIndexed { i, score ->
            if (i > 0) sb.append("  ")
            val start = sb.length
            sb.append(score.toString())
            sb.setSpan(
                ForegroundColorSpan(GradeColors.forScore(holder.itemView.context, score.toDouble())),
                start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        holder.tvScores.text = sb

        // по нажатию на карточку раскрываем список "дата - оценка"
        val key = item.semester + "|" + item.subject
        if (key in expanded) {
            holder.tvDetails.visibility = View.VISIBLE
            holder.tvDetails.text = detailsText(item)
        } else {
            holder.tvDetails.visibility = View.GONE
        }
        holder.itemView.setOnClickListener {
            if (!expanded.add(key)) expanded.remove(key)
            notifyDataSetChanged()
        }
    }

    private fun detailsText(item: SubjectGrades): String =
        item.scores.mapIndexed { i, score ->
            val date = item.dates.getOrNull(i)?.let { GradeDates.toDisplay(it) } ?: "дата неизвестна"
            "$date  —  $score"
        }.joinToString("\n")
}
