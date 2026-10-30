package com.agenda.client

interface AppHost {
    /** Последний показанный список оценок */
    val grades: List<SubjectGrades>

    /** Строка статуса */
    val status: String

    /** Логин */
    val username: String
    val accountType: AccountType

    val progress: RefreshProgress?

    val progressHidden: Boolean

    fun hideProgress()


    fun setStateListener(listener: (() -> Unit)?)


    fun requestRefresh()

    val extras: PortalExtras

    fun recentGrades(): List<FeedItem>

    val updateStatus: UpdateStatus

    fun checkForUpdates(onResult: (UpdateCheckResult) -> Unit)

    fun openUpdate(info: UpdateInfo)

    fun confirmLogout()
}
