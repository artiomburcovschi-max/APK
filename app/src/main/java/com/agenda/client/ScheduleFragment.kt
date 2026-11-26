package com.agenda.client

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.util.TimeZone

/**
 * Раздел «Расписание»: сверху живая карточка (какой сейчас урок или перемена, сколько осталось, что дальше),
 * ниже неделя по дням и уроки выбранного дня. Все расчёты в LessonClock (чистый Kotlin, под тестами);
 * здесь только отрисовка и секундный тик, который работает, пока экран на виду.
 */
class ScheduleFragment : Fragment(R.layout.fragment_schedule) {

    private class Views(root: View) {
        val title: TextView = root.findViewById(R.id.tvLiveTitle)
        val countdown: TextView = root.findViewById(R.id.tvCountdown)
        val caption: TextView = root.findViewById(R.id.tvLiveCaption)
        val progress: LinearProgressIndicator = root.findViewById(R.id.liveProgress)
        val next: TextView = root.findViewById(R.id.tvLiveNext)
        val prevWeek: Button = root.findViewById(R.id.btnPrevWeek)
        val nextWeek: Button = root.findViewById(R.id.btnNextWeek)
        val weekTitle: TextView = root.findViewById(R.id.tvWeekTitle)
        val dayTabs: LinearLayout = root.findViewById(R.id.dayTabs)
        val dayTitle: TextView = root.findViewById(R.id.tvScheduleDayTitle)
        val daySummary: TextView = root.findViewById(R.id.tvScheduleSummary)
        val empty: TextView = root.findViewById(R.id.tvScheduleEmpty)
        val container: LinearLayout = root.findViewById(R.id.scheduleContainer)
        val promo: View = root.findViewById(R.id.cardNotifyPromo)
        val promoEnable: Button = root.findViewById(R.id.btnPromoEnable)
        val promoHide: Button = root.findViewById(R.id.btnPromoHide)
    }

    private var host: AppHost? = null
    private var ui: Views? = null
    private val handler = Handler(Looper.getMainLooper())

    private var selectedDate: String? = null
    private var weekOffset = 0

    /** По чему узнаём, что урок сменился и список надо перерисовать (подсветка текущего урока). */
    private var lastStateKey: String? = null

    /** Ответ на запрос разрешения показывать уведомления (Android 13+): карточка «Включить» просит его сама. */
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val ctx = context?.applicationContext ?: return@registerForActivityResult
        if (granted) {
            LessonNotifier.setEnabled(ctx, true)
        } else {
            Toast.makeText(
                ctx,
                "Без разрешения уведомление показать нельзя. Разрешить можно в настройках телефона.",
                Toast.LENGTH_LONG
            ).show()
        }
        updatePromo()
    }

    private val ticker = object : Runnable {
        override fun run() {
            updateLive()
            // следующий тик на границе секунды: цифры меняются ровно, без дрожания
            handler.postDelayed(this, 1000L - System.currentTimeMillis() % 1000L + 15L)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val h = requireActivity() as AppHost
        host = h
        val views = Views(view)
        ui = views

        selectedDate = savedInstanceState?.getString(KEY_DATE)
        weekOffset = savedInstanceState?.getInt(KEY_WEEK) ?: 0

        views.prevWeek.setOnClickListener { weekOffset--; selectedDate = null; renderList() }
        views.nextWeek.setOnClickListener { weekOffset++; selectedDate = null; renderList() }

        views.promoEnable.setOnClickListener { enableNotification() }
        views.promoHide.setOnClickListener {
            LessonNotifier.hidePromo(requireContext().applicationContext)
            updatePromo()
        }
        updatePromo()

        renderList()
        updateLive()

        // После обновления оценок в памяти могли появиться новые уроки
        ScreenRefresh.listen(h) { renderList(); updateLive() }
    }

    override fun onResume() {
        super.onResume()
        // Тик живёт только пока экран на виду: в фоне и при погашенном экране батарею не тратим
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        renderList() // пока экрана не было, мог начаться другой урок или день
        updatePromo() // уведомление могли включить или выключить в «Настройках»
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    // ---------- карточка «есть уведомление об уроке» ----------

    private fun updatePromo() {
        val ctx = context?.applicationContext ?: return
        val views = ui ?: return
        val show = NotifyPromptPolicy.shouldShow(
            enabled = LessonNotifier.isEnabled(ctx),
            canPost = LessonNotifier.canPost(ctx),
            hidden = LessonNotifier.isPromoHidden(ctx)
        )
        views.promo.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun enableNotification() {
        val ctx = requireContext().applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) // включим, когда разрешат
            return
        }
        LessonNotifier.setEnabled(ctx, true)
        if (!LessonNotifier.canPost(ctx)) {
            Toast.makeText(ctx, "Уведомления для приложения выключены в настройках телефона.", Toast.LENGTH_LONG).show()
        }
        updatePromo()
    }

    // ---------- живая карточка ----------

    private fun updateLive() {
        val h = host ?: return
        val views = ui ?: return
        val now = System.currentTimeMillis()
        val zone = TimeZone.getDefault()

        val state = LessonClock.stateAt(h.extras.lessons, now, zone)
        val info = LessonClock.info(state, now, zone)

        views.title.text = info.title

        val countdown = info.countdownMs
        if (countdown == null) {
            views.countdown.visibility = View.GONE
        } else {
            views.countdown.visibility = View.VISIBLE
            views.countdown.text = LessonClock.formatRemaining(countdown)
            // перемена зелёная, урок синий: цвет сразу говорит, что сейчас
            val colorRes = if (state is ClockState.Break) R.color.grade_excellent else R.color.wyg_primary
            views.countdown.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
        }

        views.caption.text = info.caption
        views.caption.visibility = if (info.caption.isEmpty()) View.GONE else View.VISIBLE

        val progress = info.progress
        if (progress == null) {
            views.progress.visibility = View.GONE
        } else {
            views.progress.visibility = View.VISIBLE
            views.progress.setProgressCompat((progress * 1000f).toInt(), false)
        }

        views.next.text = info.nextLine.orEmpty()
        views.next.visibility = if (info.nextLine == null) View.GONE else View.VISIBLE

        val key = when (state) {
            is ClockState.InLesson -> "L" + state.current.lesson.order
            is ClockState.Break -> "B" + state.previous.lesson.order
            else -> state.javaClass.simpleName
        }
        if (key != lastStateKey) {
            lastStateKey = key
            renderList()
        }
    }

    // ---------- неделя и список дня ----------

    private fun renderList() {
        val h = host ?: return
        val views = ui ?: return
        val ctx = requireContext()
        val now = System.currentTimeMillis()
        val zone = TimeZone.getDefault()

        val today = LessonClock.isoOf(now, zone)
        val lessons = h.extras.lessons
        val datesWithLessons = lessons.map { it.date }.toSet()
        val week = LessonClock.weekDates(today, weekOffset)
        if (week.isEmpty()) return

        val selected = selectedDate?.takeIf { it in week }
            ?: if (today in week) today else (week.firstOrNull { it in datesWithLessons } ?: week.first())
        selectedDate = selected

        views.weekTitle.text = (if (weekOffset == 0) "Эта неделя · " else "") +
            shortDate(week.first()) + " – " + shortDate(week.last())

        // дни недели: выбранный залит синим, сегодняшний подсвечен, дни без уроков бледные
        val names = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
        views.dayTabs.removeAllViews()
        week.forEachIndexed { i, date ->
            val chip = TextView(ctx)
            chip.text = names[i] + "\n" + (date.takeLast(2).toIntOrNull()?.toString() ?: "")
            chip.gravity = Gravity.CENTER
            chip.textSize = 14f
            chip.minWidth = UiStyle.dp(ctx, 52)
            chip.setPadding(UiStyle.dp(ctx, 10), UiStyle.dp(ctx, 8), UiStyle.dp(ctx, 10), UiStyle.dp(ctx, 8))
            val bg = GradientDrawable()
            bg.cornerRadius = UiStyle.dp(ctx, 12).toFloat()
            when {
                date == selected -> {
                    bg.setColor(ContextCompat.getColor(ctx, R.color.wyg_primary))
                    chip.setTextColor(ContextCompat.getColor(ctx, R.color.wyg_on_primary))
                }
                date == today -> {
                    bg.setColor(ContextCompat.getColor(ctx, R.color.wyg_accent_bg))
                    chip.setTextColor(ContextCompat.getColor(ctx, R.color.wyg_accent_text))
                }
                else -> chip.setTextColor(ContextCompat.getColor(ctx, R.color.wyg_text_secondary))
            }
            chip.background = bg
            chip.alpha = if (date in datesWithLessons) 1f else 0.45f
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.marginEnd = UiStyle.dp(ctx, 6)
            chip.layoutParams = lp
            chip.setOnClickListener { selectedDate = date; renderList() }
            views.dayTabs.addView(chip)
        }

        // уроки выбранного дня
        val day = lessons.filter { it.date == selected }
        views.dayTitle.text = DiaryLogic.dayTitle(selected) + if (selected == today) " · сегодня" else ""
        views.daySummary.text = if (day.isEmpty()) "" else DiaryLogic.summary(day)
        views.empty.visibility = if (day.isEmpty()) View.VISIBLE else View.GONE

        views.container.removeAllViews()
        val slotByOrder = LessonClock.slots(lessons, selected, zone).associateBy { it.lesson.order }
        val inflater = LayoutInflater.from(ctx)
        for (row in LessonClock.dayRows(day)) {
            when (row) {
                is DayRow.GapRow -> {
                    val gap = TextView(ctx)
                    val word = if (row.isWindow) "окно" else "перемена"
                    gap.text = word + " · " + row.minutes + " " + RuWords.plural(row.minutes, "минута", "минуты", "минут")
                    gap.gravity = Gravity.CENTER
                    gap.textSize = 12f
                    gap.setTextColor(ContextCompat.getColor(ctx, R.color.wyg_text_secondary))
                    gap.setPadding(0, UiStyle.dp(ctx, 4), 0, UiStyle.dp(ctx, 4))
                    views.container.addView(gap)
                }
                is DayRow.LessonRow -> {
                    val card = inflater.inflate(R.layout.item_schedule_row, views.container, false) as CardView
                    val lesson = row.lesson
                    card.findViewById<TextView>(R.id.tvRowOrder).text = lesson.order.toString()
                    card.findViewById<TextView>(R.id.tvRowSubject).text = lesson.subject
                    card.findViewById<TextView>(R.id.tvRowTeacher).apply {
                        text = lesson.teacher
                        visibility = if (lesson.teacher.isEmpty()) View.GONE else View.VISIBLE
                    }
                    card.findViewById<TextView>(R.id.tvRowTime).text = row.timeText

                    // сегодня: текущий урок подсвечен, прошедшие бледные
                    val slot = slotByOrder[lesson.order]
                    if (selected == today && slot != null) {
                        if (now >= slot.startMs && now < slot.endMs) {
                            card.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.wyg_accent_bg))
                        } else if (now >= slot.endMs) {
                            card.alpha = 0.5f
                        }
                    }
                    views.container.addView(card)
                }
            }
        }
    }

    private fun shortDate(iso: String): String =
        if (iso.length >= 10) iso.substring(8, 10) + "." + iso.substring(5, 7) else iso

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_DATE, selectedDate)
        outState.putInt(KEY_WEEK, weekOffset)
    }

    override fun onDestroyView() {
        handler.removeCallbacks(ticker)
        host?.setStateListener(null) // слушатель в AppHost один на всё приложение: снимаем за собой
        host = null
        ui = null
        lastStateKey = null
        super.onDestroyView()
    }

    private companion object {
        const val KEY_DATE = "schedule_date"
        const val KEY_WEEK = "schedule_week"
    }
}
