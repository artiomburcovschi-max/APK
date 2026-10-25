package com.agenda.client

import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText

/**
 * Вкладка «Цель» в аналитике: выбор предмета, ввод целевого среднего и расчёт, что для неё нужно.
 *
 * Здесь только экран. Вся математика в GoalPlanner, все тексты в GoalText (обе части чистый Kotlin
 * с тестами), поэтому этот класс почти ничего не решает сам: берёт ввод, зовёт планировщик, рисует ответ.
 */
class GoalTab(root: View, private val store: GoalStore) {

    private val context = root.context
    private val actvSubject = root.findViewById<MaterialAutoCompleteTextView>(R.id.actvGoalSubject)
    private val etTarget = root.findViewById<TextInputEditText>(R.id.etGoalTarget)
    private val chips = root.findViewById<ChipGroup>(R.id.chipsGoalTarget)
    private val tvHint = root.findViewById<TextView>(R.id.tvGoalHint)
    private val card = root.findViewById<MaterialCardView>(R.id.cardGoal)
    private val tvVerdict = root.findViewById<TextView>(R.id.tvGoalVerdict)
    private val tvNow = root.findViewById<TextView>(R.id.tvGoalNow)
    private val tvOptionsTitle = root.findViewById<TextView>(R.id.tvGoalOptionsTitle)
    private val optionsContainer = root.findViewById<LinearLayout>(R.id.goalOptionsContainer)
    private val tvNote = root.findViewById<TextView>(R.id.tvGoalNote)

    private var semester = ""
    private var subjects: List<SubjectGrades> = emptyList() // только предметы с оценками
    private var selected: SubjectGrades? = null

    // true, пока поле цели меняем мы сами (подстановка сохранённой цели): это не «ввод пользователя»
    private var updatingTarget = false

    init {
        tvNote.text = GoalText.NOTE

        actvSubject.setOnItemClickListener { _, _, position, _ -> select(subjects.getOrNull(position)) }

        etTarget.doAfterTextChanged { if (!updatingTarget) onTargetEdited() }

        // Подсказки (7, 8, 9, 9.5) просто вписывают число в поле; дальше работает обычный ввод
        for (i in 0 until chips.childCount) {
            val chip = chips.getChildAt(i) as Chip
            chip.setOnClickListener {
                etTarget.setText(chip.text)
                etTarget.setSelection(etTarget.text?.length ?: 0)
            }
        }
    }

    /** Вызывается при открытии и при смене семестра. Выбранный предмет сохраняем, если он остался. */
    fun bind(semester: String, items: List<SubjectGrades>) {
        this.semester = semester
        subjects = items.filter { it.scores.isNotEmpty() } // у предмета без оценок среднего нет
        actvSubject.setAdapter(
            ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, subjects.map { it.subject })
        )
        select(subjects.firstOrNull { it.subject == selected?.subject } ?: subjects.firstOrNull())
    }

    private fun select(subject: SubjectGrades?) {
        selected = subject
        // false = не запускать фильтрацию списка по этому тексту, иначе в меню останется один пункт
        actvSubject.setText(subject?.subject ?: "", false)

        // у каждого предмета своя цель: подставляем сохранённую (или пусто)
        updatingTarget = true
        val saved = subject?.let { store.get(semester, it.subject) }
        etTarget.setText(saved?.let { GoalText.formatTarget(it) } ?: "")
        updatingTarget = false

        render()
    }

    private fun onTargetEdited() {
        val subject = selected
        if (subject != null) {
            val target = GoalPlanner.parseTarget(etTarget.text)
            // Сохраняем только осмысленную цель; пусто или мусор = цели нет (иначе после перезапуска
            // в поле вернулось бы старое значение, а не то, что видел человек)
            if (target != null && target >= GoalPlanner.MIN_GRADE && target <= GoalPlanner.MAX_GRADE) {
                store.set(semester, subject.subject, target)
            } else {
                store.clear(semester, subject.subject)
            }
        }
        render()
    }

    private fun render() {
        card.visibility = View.GONE

        val subject = selected
        if (subject == null) {
            showHint("В этом семестре пока нет предметов с оценками.")
            return
        }
        val target = GoalPlanner.parseTarget(etTarget.text)
        if (target == null) {
            showHint("Введи цель или выбери подсказку: покажу, что нужно для неё получить.")
            return
        }
        showHint("")
        showPlan(GoalPlanner.plan(subject.scores, target))
    }

    private fun showHint(text: String) {
        tvHint.text = text
        tvHint.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showPlan(plan: GoalPlan) {
        val verdict = GoalText.verdict(plan)
        tvVerdict.text = verdict.text
        tvVerdict.setTextColor(toneColor(verdict.tone))

        val now = GoalText.current(plan)
        tvNow.text = now
        tvNow.visibility = if (now.isEmpty()) View.GONE else View.VISIBLE

        val title = GoalText.optionsTitle(plan)
        tvOptionsTitle.text = title ?: ""
        tvOptionsTitle.visibility = if (title == null) View.GONE else View.VISIBLE

        optionsContainer.removeAllViews()
        val inflater = LayoutInflater.from(context)
        for (row in GoalText.rows(plan)) {
            val view = inflater.inflate(R.layout.item_goal_option, optionsContainer, false)
            view.findViewById<TextView>(R.id.tvOptGrade).apply {
                text = row.grade.toString()
                setTextColor(GradeColors.forScore(context, row.grade.toDouble()))
            }
            view.findViewById<TextView>(R.id.tvOptText).text = row.text
            view.findViewById<TextView>(R.id.tvOptHint).apply {
                text = row.hint
                visibility = if (row.hint.isEmpty()) View.GONE else View.VISIBLE
                setTextColor(toneColor(row.tone))
            }
            optionsContainer.addView(view)
        }

        card.visibility = View.VISIBLE
    }

    private fun toneColor(tone: Tone): Int = ContextCompat.getColor(
        context,
        when (tone) {
            Tone.GOOD -> R.color.grade_excellent
            Tone.WARN -> R.color.grade_mid
            Tone.BAD -> R.color.grade_bad
            Tone.NEUTRAL -> R.color.wyg_text_secondary
        }
    )
}
