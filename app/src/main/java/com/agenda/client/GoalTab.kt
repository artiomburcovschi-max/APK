package com.agenda.client

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Вкладка «Цель» в аналитике. Две оси выбора:
 *  - что: один предмет или общий средний;
 *  - период: выбранный семестр или весь год (переключатель виден, только если оценок хватает на два семестра).
 *
 * Здесь только экран. Математика в GoalPlanner (предмет) и OverallPlanner (общий средний),
 * тексты в GoalText: всё это чистый Kotlin с тестами. Класс берёт ввод, зовёт планировщик, рисует ответ.
 */
class GoalTab(root: View, private val store: GoalStore) {

    private enum class Scope { SUBJECT, OVERALL }
    private enum class Period { SEMESTER, YEAR }

    private val context = root.context
    private val toggleScope = root.findViewById<MaterialButtonToggleGroup>(R.id.toggleGoalScope)
    private val togglePeriod = root.findViewById<MaterialButtonToggleGroup>(R.id.toggleGoalPeriod)
    private val tilSubject = root.findViewById<TextInputLayout>(R.id.tilGoalSubject)
    private val actvSubject = root.findViewById<MaterialAutoCompleteTextView>(R.id.actvGoalSubject)
    private val etTarget = root.findViewById<TextInputEditText>(R.id.etGoalTarget)
    private val chips = root.findViewById<ChipGroup>(R.id.chipsGoalTarget)
    private val tvHint = root.findViewById<TextView>(R.id.tvGoalHint)
    private val card = root.findViewById<MaterialCardView>(R.id.cardGoal)
    private val tvVerdict = root.findViewById<TextView>(R.id.tvGoalVerdict)
    private val tvCurrent = root.findViewById<TextView>(R.id.tvGoalCurrent)
    private val optionsContainer = root.findViewById<LinearLayout>(R.id.goalOptionsContainer)
    private val tvNote = root.findViewById<TextView>(R.id.tvGoalNote)

    // «А что если»
    private val cardWhatIf = root.findViewById<MaterialCardView>(R.id.cardWhatIf)
    private val chipsWhatIf = root.findViewById<ChipGroup>(R.id.chipsWhatIf)
    private val tvWhatIfAdded = root.findViewById<TextView>(R.id.tvWhatIfAdded)
    private val tvWhatIfResult = root.findViewById<TextView>(R.id.tvWhatIfResult)
    private val btnWhatIfReset = root.findViewById<Button>(R.id.btnWhatIfReset)
    private val whatIfGrades = mutableListOf<Int>() // гипотетические оценки, живут только на экране

    private var semester = ""
    private var semesterItems: List<SubjectGrades> = emptyList()
    private var allItems: List<SubjectGrades> = emptyList()

    private var scope = Scope.SUBJECT
    private var period = Period.SEMESTER
    private var yearAvailable = false

    private var subjects: List<SubjectScores> = emptyList() // оценки за выбранный период, только предметы с оценками
    private var selected: SubjectScores? = null

    // true, пока поле цели или переключатель меняем мы сами: это не «действие пользователя»
    private var updatingTarget = false
    private var syncingToggle = false

    init {
        actvSubject.setOnItemClickListener { _, _, position, _ ->
            selected = subjects.getOrNull(position)
            onSelectionChanged()
        }

        toggleScope.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (syncingToggle || !isChecked) return@addOnButtonCheckedListener
            scope = if (checkedId == R.id.btnScopeOverall) Scope.OVERALL else Scope.SUBJECT
            refresh()
        }
        togglePeriod.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (syncingToggle || !isChecked) return@addOnButtonCheckedListener
            period = if (checkedId == R.id.btnPeriodYear) Period.YEAR else Period.SEMESTER
            refresh()
        }

        etTarget.doAfterTextChanged { if (!updatingTarget) onTargetEdited() }

        for (i in 0 until chipsWhatIf.childCount) {
            val chip = chipsWhatIf.getChildAt(i) as Chip
            chip.setOnClickListener {
                if (whatIfGrades.size < WhatIf.MAX_ADDED) whatIfGrades.add(chip.text.toString().toInt())
                renderWhatIf()
            }
        }
        btnWhatIfReset.setOnClickListener {
            whatIfGrades.clear()
            renderWhatIf()
        }

        // Подсказки (7, 8, 9, 9.5) просто вписывают число в поле; дальше работает обычный ввод
        for (i in 0 until chips.childCount) {
            val chip = chips.getChildAt(i) as Chip
            chip.setOnClickListener {
                etTarget.setText(chip.text)
                etTarget.setSelection(etTarget.text?.length ?: 0)
            }
        }
    }

    /**
     * Вызывается при открытии и при смене семестра.
     * semesterItems = предметы выбранного семестра, allItems = все семестры (нужны для периода «Год»).
     */
    fun bind(semester: String, semesterItems: List<SubjectGrades>, allItems: List<SubjectGrades>) {
        this.semester = semester
        this.semesterItems = semesterItems
        this.allItems = allItems
        // «Год» отличается от «Семестра», только когда оценки есть минимум в двух семестрах
        yearAvailable = allItems.filter { it.scores.isNotEmpty() }.map { it.semester }.distinct().size >= 2
        refresh()
    }

    /** Пересобирает всё после смены предмета/периода/данных: список предметов, цель, ответ. */
    private fun refresh() {
        if (!yearAvailable && period == Period.YEAR) {
            period = Period.SEMESTER
            syncingToggle = true
            togglePeriod.check(R.id.btnPeriodSemester)
            syncingToggle = false
        }
        togglePeriod.visibility = if (yearAvailable) View.VISIBLE else View.GONE

        subjects = if (period == Period.YEAR) GoalScope.forYear(allItems) else GoalScope.forSemester(semesterItems)
        // Выбранный предмет сохраняем, если он остался в списке
        selected = subjects.firstOrNull { it.name == selected?.name } ?: subjects.firstOrNull()

        actvSubject.setAdapter(
            ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, subjects.map { it.name })
        )
        // false = не запускать фильтрацию списка по этому тексту, иначе в меню останется один пункт
        actvSubject.setText(selected?.name ?: "", false)
        tilSubject.visibility = if (scope == Scope.SUBJECT) View.VISIBLE else View.GONE

        onSelectionChanged()
    }

    private fun onSelectionChanged() {
        whatIfGrades.clear() // другой предмет, период или данные: прежние гипотетические оценки не к месту
        showCurrent()
        loadTarget()
        render()
        renderWhatIf()
    }

    /** «А что если»: карточка только для одного предмета; считает и показывает, ничего не сохраняет. */
    private fun renderWhatIf() {
        val subject = selected
        if (scope != Scope.SUBJECT || subject == null) {
            cardWhatIf.visibility = View.GONE
            return
        }
        cardWhatIf.visibility = View.VISIBLE

        val added = WhatIf.addedText(whatIfGrades)
        tvWhatIfAdded.text = added
        tvWhatIfAdded.visibility = if (added.isEmpty()) View.GONE else View.VISIBLE
        btnWhatIfReset.visibility = if (whatIfGrades.isEmpty()) View.GONE else View.VISIBLE

        val lines = if (whatIfGrades.isEmpty()) null else WhatIf.compute(subjects, subject.name, whatIfGrades)
        if (lines == null) {
            tvWhatIfResult.visibility = View.GONE
            return
        }
        val text = SpannableStringBuilder()
        for ((index, line) in lines.withIndex()) {
            if (index > 0) text.append("\n")
            text.append(line.text).append("  ")
            val start = text.length
            text.append(line.deltaText)
            text.setSpan(ForegroundColorSpan(toneColor(line.tone)), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text.setSpan(StyleSpan(Typeface.BOLD), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        tvWhatIfResult.text = text
        tvWhatIfResult.visibility = View.VISIBLE
    }

    // ----- цель: хранение -----

    /** Семестр в ключе хранения: для года отдельный ключ, чтобы цели «за год» и «за семестр» не смешивались. */
    private fun storeSemester() = if (period == Period.YEAR) "year" else semester

    /** Предмет в ключе хранения: у общего среднего свой служебный ключ, он не совпадёт с названием предмета. */
    private fun storeSubject(): String? = if (scope == Scope.OVERALL) OVERALL_KEY else selected?.name

    private fun loadTarget() {
        updatingTarget = true
        val saved = storeSubject()?.let { store.get(storeSemester(), it) }
        etTarget.setText(saved?.let { GoalText.formatTarget(it) } ?: "")
        updatingTarget = false
    }

    private fun onTargetEdited() {
        val key = storeSubject()
        if (key != null) {
            val target = GoalPlanner.parseTarget(etTarget.text)
            // Сохраняем только осмысленную цель; пусто или мусор = цели нет (иначе после перезапуска
            // в поле вернулось бы старое значение, а не то, что видел человек)
            if (target != null && target >= GoalPlanner.MIN_GRADE && target <= GoalPlanner.MAX_GRADE) {
                store.set(storeSemester(), key, target)
            } else {
                store.clear(storeSemester(), key)
            }
        }
        render()
    }

    // ----- вывод -----

    private fun render() {
        card.visibility = View.GONE
        tvNote.text = GoalText.noteFor(period == Period.YEAR)

        if (subjects.isEmpty()) {
            showHint("Пока нет предметов с оценками.")
            return
        }
        val target = GoalPlanner.parseTarget(etTarget.text)
        if (target == null) {
            showHint("Введи цель или выбери подсказку: покажу, что нужно для неё получить.")
            return
        }
        showHint("")

        if (scope == Scope.OVERALL) {
            val plan = OverallPlanner.plan(subjects, target)
            showCard(GoalText.overallVerdict(plan), GoalText.overallSections(plan))
        } else {
            val subject = selected ?: return
            val plan = GoalPlanner.plan(subject.scores, target)
            showCard(GoalText.verdict(plan), GoalText.sections(plan))
        }
    }

    private fun showHint(text: String) {
        tvHint.text = text
        tvHint.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
    }

    /** «Сейчас: 8.50 · 4 оценки» (или «· 9 предметов» для общего): средний виден сразу, до ввода цели. */
    private fun showCurrent() {
        val average: Double
        val line: String
        if (scope == Scope.OVERALL) {
            if (subjects.isEmpty()) return hideCurrent()
            average = subjects.map { it.scores.average() }.average() // среднее из средних, как в «Обзоре»
            line = GoalText.currentOverall(average, subjects.size)
        } else {
            val subject = selected ?: return hideCurrent()
            average = subject.scores.average()
            line = GoalText.current(subject.scores)
        }

        val text = SpannableStringBuilder(line)
        // число среднего красим по шкале оценок (так же, как в «Обзоре») и делаем жирным
        val number = GoalText.format(average)
        val start = line.indexOf(number)
        if (start >= 0) {
            val end = start + number.length
            text.setSpan(
                ForegroundColorSpan(GradeColors.forScore(context, average)),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            text.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        tvCurrent.text = text
        tvCurrent.visibility = View.VISIBLE
    }

    private fun hideCurrent() {
        tvCurrent.visibility = View.GONE
    }

    private fun showCard(verdict: GoalVerdict, sections: List<GoalSection>) {
        tvVerdict.text = verdict.text
        tvVerdict.setTextColor(toneColor(verdict.tone))

        optionsContainer.removeAllViews()
        val inflater = LayoutInflater.from(context)
        for (section in sections) {
            val title = inflater.inflate(R.layout.item_goal_section, optionsContainer, false) as TextView
            title.text = section.title
            optionsContainer.addView(title)

            for (row in section.rows) {
                val view = inflater.inflate(R.layout.item_goal_option, optionsContainer, false)
                view.findViewById<TextView>(R.id.tvOptGrade).apply {
                    text = row.lead
                    // у строк с оценкой метка красится её цветом, у серий (слева число оценок) нейтрально
                    setTextColor(
                        row.grade?.let { GradeColors.forScore(context, it.toDouble()) }
                            ?: ContextCompat.getColor(context, R.color.wyg_text_secondary)
                    )
                }
                view.findViewById<TextView>(R.id.tvOptText).text = row.text
                view.findViewById<TextView>(R.id.tvOptHint).apply {
                    text = row.hint
                    visibility = if (row.hint.isEmpty()) View.GONE else View.VISIBLE
                    setTextColor(toneColor(row.tone))
                }
                optionsContainer.addView(view)
            }
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

    private companion object {
        const val OVERALL_KEY = "*общий*"
    }
}
