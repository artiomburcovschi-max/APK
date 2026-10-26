package com.agenda.client

import java.util.Locale

/** Окраска текста; Android-цвета подставляет экран (здесь Android нет, поэтому это можно тестировать). */
enum class Tone { GOOD, WARN, BAD, NEUTRAL }

data class GoalVerdict(val text: String, val tone: Tone)

/**
 * Строка варианта. lead = крупная метка слева; если задан grade, она красится цветом этой оценки,
 * иначе нейтрально (так у серий, где слева число оценок).
 */
data class GoalRow(val lead: String, val grade: Int?, val text: String, val hint: String, val tone: Tone)

/** Блок строк с заголовком: «Одной оценкой», «Серией оценок». */
data class GoalSection(val title: String, val rows: List<GoalRow>)

/** Превращает GoalPlan в тексты для экрана «Цель». Чистый Kotlin. */
object GoalText {

    const val NOTE =
        "Расчёт по среднему арифметическому без округления. " +
            "Оценка за сессию (теза) пока не учитывается."

    /** Примечание под расчётом; за год добавляем, как именно он считается. */
    fun noteFor(year: Boolean): String =
        if (year) NOTE + " За год все оценки всех семестров считаются вместе." else NOTE

    fun format(value: Double): String = String.format(Locale.US, "%.2f", value)

    /** Цель для поля ввода: 9.0 -> "9", 8.5 -> "8.5", 8.75 -> "8.75". */
    fun formatTarget(value: Double): String =
        if (value == Math.floor(value)) value.toInt().toString() else value.toString()

    /** «Сейчас: 8.50 · 4 оценки» (пусто, если оценок нет). */
    fun current(scores: List<Int>): String =
        if (scores.isEmpty()) "" else
            "Сейчас: ${format(scores.average())} · ${RuWords.grades(scores.size)}"

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

    fun sections(plan: GoalPlan): List<GoalSection> = when (plan.status) {
        GoalStatus.REACHABLE -> reachableSections(plan)
        GoalStatus.ACHIEVED -> listOf(achievedSection(plan))
        else -> emptyList()
    }

    private fun reachableSections(plan: GoalPlan): List<GoalSection> {
        val sections = mutableListOf<GoalSection>()

        // 1. Одна и та же оценка каждый раз
        val single = plan.options.map {
            val (label, tone) = difficultyLabel(GoalPlanner.difficultyOf(it.count))
            GoalRow(it.grade.toString(), it.grade, RuWords.gradeCount(it.grade, it.count), label, tone)
        }.toMutableList()
        // Оценка, равная цели (девятки при цели 9): объясняем, почему её нет среди вариантов
        plan.equalGrade?.let { g ->
            single.add(
                GoalRow(
                    g.toString(), g,
                    "Одни ${RuWords.gradePlural(g)} не помогут",
                    "средний будет приближаться к ${format(plan.target)}, но не дойдёт",
                    Tone.NEUTRAL
                )
            )
        }
        sections.add(GoalSection("Одной и той же оценкой (хватит любого одного варианта):", single))

        // 2. Серия из разных оценок
        if (plan.series.isNotEmpty()) {
            val rows = plan.series.map { option ->
                GoalRow(
                    option.count.toString(), null,
                    option.example.joinToString(" + ") { RuWords.gradeCount(it.grade, it.count) },
                    "всего ${RuWords.grades(option.count)}, сумма не меньше ${option.minSum}",
                    Tone.NEUTRAL
                )
            }
            sections.add(GoalSection("Серией разных оценок (пример, сочетаний может быть больше):", rows))
        }
        return sections
    }

    private fun achievedSection(plan: GoalPlan): GoalSection =
        if (plan.options.isEmpty()) {
            GoalSection("Запаса нет: любая оценка ниже цели опустит средний под цель.", emptyList())
        } else {
            GoalSection(
                "Запас: сколько таких оценок ещё можно получить и остаться на цели:",
                plan.options.map {
                    GoalRow(
                        it.grade.toString(), it.grade,
                        "максимум " + RuWords.gradeCount(it.grade, it.count), "", Tone.NEUTRAL
                    )
                }
            )
        }

    fun difficultyLabel(d: GoalDifficulty): Pair<String, Tone> = when (d) {
        GoalDifficulty.EASY -> "реально" to Tone.GOOD
        GoalDifficulty.HARD -> "сложно" to Tone.WARN
        GoalDifficulty.VERY_HARD -> "очень много" to Tone.BAD
    }

    // ---------- общий средний ----------

    /** «Сейчас: 8.43 · 9 предметов». */
    fun currentOverall(overall: Double, subjectsCount: Int): String =
        if (subjectsCount == 0) "" else
            "Сейчас: ${format(overall)} · " +
                "$subjectsCount " + RuWords.plural(subjectsCount, "предмет", "предмета", "предметов")

    fun overallVerdict(plan: OverallPlan): GoalVerdict {
        val target = format(plan.target)
        return when (plan.status) {
            GoalStatus.NO_DATA -> GoalVerdict("Пока нет оценок", Tone.NEUTRAL)
            GoalStatus.INVALID_TARGET -> GoalVerdict("Цель должна быть от 1 до 10", Tone.NEUTRAL)
            GoalStatus.ACHIEVED ->
                GoalVerdict("Общий средний ${format(plan.overall)}: цель $target уже достигнута", Tone.GOOD)
            GoalStatus.UNREACHABLE ->
                if (plan.target >= GoalPlanner.MAX_GRADE) {
                    GoalVerdict(
                        "Средний 10.00 недостижим: пока есть оценки ниже десятки, он только " +
                            "приближается к 10. Поставь, например, 9.50",
                        Tone.BAD
                    )
                } else {
                    GoalVerdict("Цель $target недостижима на практике", Tone.BAD)
                }
            GoalStatus.REACHABLE -> when (plan.difficulty) {
                GoalDifficulty.EASY -> GoalVerdict("Общий средний $target реален", Tone.GOOD)
                GoalDifficulty.HARD ->
                    GoalVerdict("Общий средний $target достижим, но нужна серия сильных оценок", Tone.WARN)
                else ->
                    GoalVerdict("Общий средний $target почти нереален: слишком много отличных оценок", Tone.BAD)
            }
        }
    }

    fun overallSections(plan: OverallPlan): List<GoalSection> {
        if (plan.status != GoalStatus.REACHABLE) return emptyList()
        val sections = mutableListOf<GoalSection>()

        // 1. Поровну по всем предметам
        val evenRows = plan.even.map {
            val (label, tone) = if (it.grade == 10) {
                difficultyLabel(OverallPlanner.difficultyOf(it.perSubject))
            } else {
                "" to Tone.NEUTRAL
            }
            val hint = (if (label.isEmpty()) "" else "$label, ") + "всего ${RuWords.grades(it.total)}"
            GoalRow(it.grade.toString(), it.grade, "по " + RuWords.gradeCount(it.grade, it.perSubject) + " в каждом предмете", hint, tone)
        }
        sections.add(GoalSection("Подтягивать все предметы поровну (хватит любого одного варианта):", evenRows))

        // 2. Все новые оценки в одном предмете
        if (plan.single.isNotEmpty() || plan.cannotAlone.isNotEmpty()) {
            val rows = plan.single.map { row ->
                val best = row.options.first()
                GoalRow(
                    best.grade.toString(), best.grade, row.subject,
                    "нужно " + row.options.joinToString(" или ") { RuWords.gradeCount(it.grade, it.count) } +
                        " (сейчас ${format(row.average)})",
                    Tone.NEUTRAL
                )
            }.toMutableList()
            if (plan.cannotAlone.isNotEmpty()) {
                rows.add(
                    GoalRow(
                        "–", null, plan.cannotAlone.joinToString(", "),
                        "одним этим предметом цель не достичь даже десятками", Tone.NEUTRAL
                    )
                )
            }
            sections.add(GoalSection("Только в одном предмете (все новые оценки в нём):", rows))
        }
        return sections
    }
}
