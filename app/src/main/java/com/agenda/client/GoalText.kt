package com.agenda.client

import java.util.Locale

/** Окраска текста; Android-цвета подставляет экран (здесь Android нет, поэтому это можно тестировать). */
enum class Tone { GOOD, WARN, BAD, NEUTRAL }

data class GoalVerdict(val text: String, val tone: Tone)

/** Строка варианта: оценка слева (красится по GradeColors), текст и пометка справа. */
data class GoalRow(val grade: Int, val text: String, val hint: String, val tone: Tone)

/** Превращает GoalPlan в тексты для экрана «Цель». Чистый Kotlin. */
object GoalText {

    const val NOTE =
        "Расчёт по среднему арифметическому без округления. " +
            "Оценка за сессию (теза) пока не учитывается."

    fun format(value: Double): String = String.format(Locale.US, "%.2f", value)

    /** Цель для поля ввода: 9.0 -> "9", 8.5 -> "8.5", 8.75 -> "8.75". */
    fun formatTarget(value: Double): String =
        if (value == Math.floor(value)) value.toInt().toString() else value.toString()

    fun verdict(plan: GoalPlan): GoalVerdict {
        val target = format(plan.target)
        return when (plan.status) {
            GoalStatus.NO_DATA ->
                GoalVerdict("По этому предмету пока нет оценок", Tone.NEUTRAL)
            GoalStatus.INVALID_TARGET ->
                GoalVerdict("Цель должна быть от 1 до 10", Tone.NEUTRAL)
            GoalStatus.ACHIEVED ->
                GoalVerdict("Цель $target уже достигнута", Tone.GOOD)
            GoalStatus.UNREACHABLE ->
                GoalVerdict(
                    "Средний 10.00 недостижим: пока есть оценки ниже десятки, он только " +
                        "приближается к 10. Поставь, например, 9.50",
                    Tone.BAD
                )
            GoalStatus.REACHABLE -> when (plan.difficulty) {
                GoalDifficulty.EASY -> GoalVerdict("Цель $target реальна", Tone.GOOD)
                GoalDifficulty.HARD ->
                    GoalVerdict("Цель $target достижима, но нужна серия сильных оценок", Tone.WARN)
                else ->
                    GoalVerdict(
                        "Цель $target почти нереальна: слишком много отличных оценок подряд",
                        Tone.BAD
                    )
            }
        }
    }

    /** «Сейчас: 8.00 · 5 оценок» (пусто, если оценок нет). */
    fun current(plan: GoalPlan): String =
        if (plan.gradesCount == 0) "" else
            "Сейчас: ${format(plan.average)} · ${RuWords.grades(plan.gradesCount)}"

    /** Заголовок над списком вариантов (null = список не нужен). */
    fun optionsTitle(plan: GoalPlan): String? = when (plan.status) {
        GoalStatus.REACHABLE ->
            "Достаточно любого одного варианта (если получать только такую оценку):"
        GoalStatus.ACHIEVED ->
            if (plan.options.isEmpty()) {
                "Запаса нет: любая оценка ниже цели опустит средний под цель."
            } else {
                "Запас: сколько таких оценок ещё можно получить и остаться на цели:"
            }
        else -> null
    }

    fun rows(plan: GoalPlan): List<GoalRow> = when (plan.status) {
        GoalStatus.REACHABLE -> plan.options.map {
            val (label, tone) = difficultyLabel(GoalPlanner.difficultyOf(it.count))
            GoalRow(it.grade, RuWords.gradeCount(it.grade, it.count), label, tone)
        }
        GoalStatus.ACHIEVED -> plan.options.map {
            GoalRow(it.grade, "максимум " + RuWords.gradeCount(it.grade, it.count), "", Tone.NEUTRAL)
        }
        else -> emptyList()
    }

    fun difficultyLabel(d: GoalDifficulty): Pair<String, Tone> = when (d) {
        GoalDifficulty.EASY -> "реально" to Tone.GOOD
        GoalDifficulty.HARD -> "сложно" to Tone.WARN
        GoalDifficulty.VERY_HARD -> "очень много" to Tone.BAD
    }
}
