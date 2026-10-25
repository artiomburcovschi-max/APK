package com.agenda.client

import android.os.Bundle
import android.view.View
import android.widget.RadioGroup
import androidx.fragment.app.Fragment

/** Раздел «Настройки»: пока одна настройка, тема оформления. */
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private val idToMode = mapOf(
        R.id.rbThemeSystem to ThemeMode.SYSTEM,
        R.id.rbThemeLight to ThemeMode.LIGHT,
        R.id.rbThemeDark to ThemeMode.DARK
    )

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
    }
}
