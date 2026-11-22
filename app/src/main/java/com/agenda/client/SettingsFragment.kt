package com.agenda.client

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

/** Раздел «Настройки»: тема оформления, уведомление «Урок сейчас» и анонимная статистика. */
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private val idToMode = mapOf(
        R.id.rbThemeSystem to ThemeMode.SYSTEM,
        R.id.rbThemeLight to ThemeMode.LIGHT,
        R.id.rbThemeDark to ThemeMode.DARK
    )

    private var notifySwitch: SwitchCompat? = null
    private var notifyListener: CompoundButton.OnCheckedChangeListener? = null

    /** Ответ на запрос разрешения показывать уведомления (Android 13+). */
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val ctx = context?.applicationContext ?: return@registerForActivityResult
        if (granted) {
            LessonNotifier.setEnabled(ctx, true)
        } else {
            setNotifySwitchSilently(false)
            Toast.makeText(
                ctx,
                "Без разрешения уведомление показать нельзя. Разрешить можно в настройках телефона.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext().applicationContext
        val group = view.findViewById<RadioGroup>(R.id.rgTheme)

        // Сначала отмечаем текущий выбор, и только потом вешаем слушатель: иначе отметка сама его вызовет
        val current = ThemeSettings.load(ctx)
        group.check(idToMode.entries.first { it.value == current }.key)

        group.setOnCheckedChangeListener { _, checkedId ->
            val mode = idToMode[checkedId] ?: return@setOnCheckedChangeListener
            if (mode == ThemeSettings.load(ctx)) return@setOnCheckedChangeListener
            ThemeSettings.save(ctx, mode)   // сначала сохраняем: после apply Activity пересоздастся
            ThemeSettings.apply(mode)
        }

        // Уведомление «Урок сейчас»: включено, только если разрешено и в приложении, и в системе
        val notify = view.findViewById<SwitchCompat>(R.id.swLessonNotify)
        notifySwitch = notify
        notify.isChecked = LessonNotifier.isEnabled(ctx) && LessonNotifier.canPost(ctx) // отметка до слушателя
        val listener = CompoundButton.OnCheckedChangeListener { _, on ->
            if (!on) {
                LessonNotifier.setEnabled(ctx, false)
            } else if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) // включим, когда разрешат
            } else {
                LessonNotifier.setEnabled(ctx, true)
            }
        }
        notifyListener = listener
        notify.setOnCheckedChangeListener(listener)

        // Статистика: пока адрес сервера не задан, раздела нет, настраивать нечего
        val statsSection = view.findViewById<View>(R.id.statsSection)
        if (!StatsConfig.enabled) {
            statsSection.visibility = View.GONE
            return
        }
        val store = StatsStore(ctx)
        val switch = view.findViewById<SwitchCompat>(R.id.swStats)
        switch.isChecked = store.load().consent == StatsConsent.YES // отметка до слушателя, как и у темы
        switch.setOnCheckedChangeListener { _, on ->
            store.save(store.load().copy(consent = if (on) StatsConsent.YES else StatsConsent.NO))
            if (on) StatsReporter.get(ctx).pingIfDue()
        }
    }

    /** Переставить переключатель, не запуская слушатель (иначе он снова пойдёт просить разрешение или выключать). */
    private fun setNotifySwitchSilently(checked: Boolean) {
        val s = notifySwitch ?: return
        s.setOnCheckedChangeListener(null)
        s.isChecked = checked
        s.setOnCheckedChangeListener(notifyListener)
    }

    override fun onDestroyView() {
        notifySwitch = null
        notifyListener = null
        super.onDestroyView()
    }
}
