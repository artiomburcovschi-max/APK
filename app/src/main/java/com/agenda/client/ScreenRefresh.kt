package com.agenda.client

/** Общая мелочь экранов, которые показывают данные страницы портала: перерисоваться, когда обновление закончилось. */
object ScreenRefresh {

    /**
     * Вызывает onFinished, когда очередное обновление закончилось (а не на каждый тик процента: они идут раз в секунду).
     * Слушатель в AppHost один на всё приложение, поэтому экран обязан снять его в onDestroyView.
     */
    fun listen(host: AppHost, onFinished: () -> Unit) {
        var wasLoading = host.progress != null
        host.setStateListener {
            val loading = host.progress != null
            if (wasLoading && !loading) onFinished()
            wasLoading = loading
        }
    }
}
