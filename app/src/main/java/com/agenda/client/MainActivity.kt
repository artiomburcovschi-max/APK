package com.agenda.client

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import okhttp3.*
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Хозяин приложения: боковая шторка + «движок» (скрытый WebView, вход на портал, история оценок).
 * Разделы (Оценки, Аналитика, Аккаунт, О приложении) это фрагменты, которые меняются в области content.
 * Новый раздел = пункт в res/menu/drawer_menu.xml + фрагмент + одна строка в sectionFor().
 */
class MainActivity : AppCompatActivity(), AppHost {

    private lateinit var drawer: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var webView: WebView
    private lateinit var repo: GradesRepository
    private lateinit var cache: SnapshotCache
    private lateinit var updater: UpdateChecker
    private lateinit var store: CredentialStore

    private var usernameIntent: String = ""
    private var passwordIntent: String = ""
    private var accountTypeValue: AccountType = AccountType.PARENT

    private val client = OkHttpClient()
    private val TAG = "AGENDA_DEBUG"

    // Состояние для экрана «Оценки». Живёт в Activity, поэтому переживает смену разделов.
    private var gradesState: List<SubjectGrades> = emptyList()
    private var statusState: String = ""
    private var stateListener: (() -> Unit)? = null

    private var currentNavId = R.id.nav_grades

    // ---- AppHost ----
    override val grades: List<SubjectGrades> get() = gradesState
    override val status: String get() = statusState
    override val username: String get() = usernameIntent
    override val accountType: AccountType get() = accountTypeValue

    override fun setStateListener(listener: (() -> Unit)?) {
        stateListener = listener
    }

    override fun requestRefresh() {
        val wait = RefreshPolicy.manualWaitMs(cache.lastAttemptAt, System.currentTimeMillis())
        if (wait > 0) {
            Toast.makeText(
                this,
                "Данные свежие. Обновить можно через ${(wait + 999) / 1000} с",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            startRefresh()
        }
    }

    override fun confirmLogout() {
        // Выход необратим (стирает историю), поэтому спрашиваем
        AlertDialog.Builder(this)
            .setTitle("Сменить аккаунт?")
            .setMessage("С этого телефона будут стёрты логин, пароль и вся сохранённая история оценок.")
            .setPositiveButton("Сменить") { _, _ -> performLogout() }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ---- жизненный цикл ----
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Логин и пароль берём из защищённого хранилища, а не из Intent
        store = CredentialStore(this)
        val creds = store.load()
        if (creds == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        repo = GradesRepository(this)
        cache = SnapshotCache(this)
        updater = UpdateChecker(this, client)
        usernameIntent = creds.first
        passwordIntent = creds.second
        accountTypeValue = store.loadType()

        setContentView(R.layout.activity_main)
        drawer = findViewById(R.id.drawerLayout)
        navView = findViewById(R.id.navView)
        toolbar = findViewById(R.id.toolbar)
        webView = findViewById(R.id.webView)

        setupWindowInsets()
        setupWebView()
        setupNavigation(savedInstanceState)

        // Сначала показываем сохранённое: экран не пустой, портал пока не трогаем
        val cached = cache.load()
        if (cached.isNotEmpty()) {
            gradesState = cached
            statusState = "Данные от ${formatTime(cache.lastSuccessAt)}"
        } else {
            statusState = "Нажми «Обновить», чтобы загрузить оценки"
        }

        // На портал идём только если данные устарели
        if (RefreshPolicy.shouldAutoRefresh(cache.lastAttemptAt, System.currentTimeMillis())) {
            startRefresh()
        }

        // Баннер "доступна новая версия": сначала из сохранённого, потом (раз в сутки) проверяем сервер
        showUpdateBanner()
        updater.refreshIfDue { runOnUiThread { showUpdateBanner() } }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_NAV, currentNavId)
    }

    // ---- оболочка: шторка, отступы, кнопка «назад» ----
    private fun setupWindowInsets() {
        // С targetSdk 35+ приложение рисуется под системными панелями. Сдвигаем содержимое на их высоту.
        val content = findViewById<View>(R.id.contentRoot)
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets // не «съедаем», чтобы шторка тоже получила свои отступы
        }
        // Фон светлый, значит значки в статус-баре и панели навигации должны быть тёмными
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
    }

    private fun setupNavigation(savedInstanceState: Bundle?) {
        navView.getHeaderView(0).findViewById<TextView>(R.id.tvNavUser).text = usernameIntent
        toolbar.setNavigationOnClickListener { drawer.openDrawer(GravityCompat.START) }
        navView.setNavigationItemSelectedListener { item ->
            showSection(item.itemId)
            drawer.closeDrawer(GravityCompat.START)
            true
        }

        if (savedInstanceState == null) {
            showSection(currentNavId)
        } else {
            // Поворот экрана: FragmentManager сам восстановил фрагмент, нам остаётся вернуть заголовок и пункт меню
            currentNavId = savedInstanceState.getInt(KEY_NAV, R.id.nav_grades)
            toolbar.title = navView.menu.findItem(currentNavId)?.title
            navView.setCheckedItem(currentNavId)
        }

        // «Назад»: сначала закрыть шторку, потом вернуться на «Оценки», и только потом выйти
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    drawer.isDrawerOpen(GravityCompat.START) -> drawer.closeDrawer(GravityCompat.START)
                    currentNavId != R.id.nav_grades -> showSection(R.id.nav_grades)
                    else -> finish()
                }
            }
        })
    }

    private fun sectionFor(navId: Int): Fragment = when (navId) {
        R.id.nav_analytics -> AnalyticsFragment()
        R.id.nav_account -> AccountFragment()
        R.id.nav_about -> AboutFragment()
        else -> GradesFragment()
    }

    private fun showSection(navId: Int) {
        val alreadyShown = navId == currentNavId &&
            supportFragmentManager.findFragmentById(R.id.content) != null
        if (alreadyShown) return

        supportFragmentManager.beginTransaction()
            .replace(R.id.content, sectionFor(navId))
            .commit()
        currentNavId = navId
        toolbar.title = navView.menu.findItem(navId)?.title
        navView.setCheckedItem(navId)
    }

    private fun publishState() {
        stateListener?.invoke()
    }

    // ---- движок: WebView и вход на портал (логика из v9 без изменений) ----
    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.addJavascriptInterface(WebAppInterface(), "AndroidBridge")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                Log.d(TAG, "JS: ${consoleMessage?.message()}")
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            // Не пускаем во встроенный браузер YouTube, иконки и счётчики (см. HostPolicy)
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val host = request?.url?.host ?: return null
                if (HostPolicy.isBlocked(host)) {
                    Log.d(TAG, "Заблокирован сторонний ресурс: $host")
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                return null
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "Страница загружена, URL: $url")

                if (url?.contains("agenda.sime.md") == true) {
                    // JSONObject.quote() безопасно экранирует кавычки и спецсимволы
                    val userJs = JSONObject.quote(usernameIntent)
                    val passJs = JSONObject.quote(passwordIntent)
                    // какую кнопку нажимать на стартовой странице: "Părinte" или "Elev"
                    val roleJs = JSONObject.quote(accountTypeValue.portalAlt)

                    val jsAutomationCode = """
                        (function() {
                            if (window._agendaBotStarted) return;
                            window._agendaBotStarted = true;
                            window._tableWait = 0;

                            function diag() {
                                try {
                                    console.log("DIAG width=" + window.innerWidth + " ua=" + navigator.userAgent);
                                    console.log("DIAG tables=" + Array.from(document.querySelectorAll('table[id]')).map(function(t) { return t.id; }).join(','));
                                    var s1 = document.getElementById('sem1');
                                    console.log("DIAG sem1 exists=" + !!s1 + " text=" + (s1 ? s1.innerText.substring(0, 300).replace(/\s+/g, ' ') : ''));
                                    console.log("DIAG eleve=" + apex.item('P1_ELEVE_ID').getValue() + " semester=" + apex.item('P1_SEMESTER_ID').getValue());
                                    console.log("DIAG tabs=" + Array.from(document.querySelectorAll('.t-Tabs-link')).map(function(a) { return a.innerText.trim(); }).join('|'));
                                } catch(e) { console.log("DIAG error " + e); }
                            }

                            let attempts = 0;
                            let timer = setInterval(function() {
                                attempts++;

                                // Шаг 1: после успешного входа появляется блок с оценками
                                var evalList = document.getElementById('P1_LAST_3_EVAL_LIST');
                                if (evalList && evalList.value) {
                                    var hasTable = !!document.getElementById('report_table_sem1');
                                    if (hasTable || window._tableWait >= 8) {
                                        if (!hasTable) diag();
                                        console.log("Передаем страницу в Android, таблица есть: " + hasTable);
                                        window.AndroidBridge.processHTML(document.getElementsByTagName('html')[0].innerHTML);
                                        clearInterval(timer);
                                        return;
                                    }
                                    window._tableWait++;
                                    if (window._tableWait === 3) {
                                        try {
                                            apex.region('sem1').refresh();
                                            console.log("Вызвали apex.region('sem1').refresh()");
                                        } catch(e) { console.log("refresh error: " + e); }
                                    }
                                    return;
                                }

                                // Шаг 2: ищем iframe, внутри которого реально есть форма логина
                                var loginFrame = null;
                                document.querySelectorAll('iframe').forEach(function(f) {
                                    try {
                                        if (f.contentDocument && f.contentDocument.getElementById('P101_USERNAME')) {
                                            loginFrame = f;
                                        }
                                    } catch(e) {}
                                });

                                if (loginFrame) {
                                    var d = loginFrame.contentDocument;
                                    var userField = d.getElementById('P101_USERNAME');
                                    var passField = d.getElementById('P101_PASSWORD');

                                    if (userField.value === '' && !window._loginSubmitted) {
                                        window._loginSubmitted = true;
                                        console.log("Форма логина найдена, заполняем...");
                                        userField.value = $userJs;
                                        userField.dispatchEvent(new Event('input', { bubbles: true }));
                                        passField.value = $passJs;
                                        passField.dispatchEvent(new Event('input', { bubbles: true }));

                                        setTimeout(function() {
                                            var btn = d.getElementById('P101_LOGIN')
                                                || d.querySelector('button.t-Button--hot, button[type=submit], input[type=submit]');

                                            if (!btn) {
                                                var all = d.querySelectorAll('button, input[type=button], a.t-Button, a[role=button]');
                                                for (var i = 0; i < all.length; i++) {
                                                    var t = (all[i].innerText || all[i].value || '').toLowerCase();
                                                    if (/autentific|intr|login|conect|sign/.test(t)) { btn = all[i]; break; }
                                                }
                                            }

                                            if (btn) {
                                                console.log("Кликаем кнопку входа: " + btn.outerHTML.substring(0, 200));
                                                btn.click();
                                            } else {
                                                console.log("Кнопка входа не найдена, дампим элементы iframe:");
                                                d.querySelectorAll('button, a.t-Button, input[type=button], input[type=submit], form').forEach(function(el) {
                                                    console.log("EL: " + el.outerHTML.substring(0, 400));
                                                });
                                                console.log("Пробуем Enter в поле пароля");
                                                ['keydown', 'keypress', 'keyup'].forEach(function(type) {
                                                    passField.dispatchEvent(new KeyboardEvent(type, { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }));
                                                });
                                            }
                                        }, 500);
                                    }
                                } else if (!window._parentClicked) {
                                    // Шаг 3: открываем модалку входа для родителя (один раз)
                                    var roleImg = document.querySelector('img[alt=' + $roleJs + ']');
                                    var parentLink = roleImg && roleImg.closest('a');
                                    if (parentLink) {
                                        window._parentClicked = true;
                                        console.log("Кликаем по кнопке входа: " + $roleJs);
                                        parentLink.click();
                                    }
                                }

                                if (attempts > 30) {
                                    console.log("Таймаут автоматизации истек.");
                                    window.AndroidBridge.onTimeout();
                                    clearInterval(timer);
                                }
                            }, 1000);
                        })();
                    """.trimIndent()

                    webView.evaluateJavascript(jsAutomationCode, null)
                }
            }
        }
    }

    private fun performLogout() {
        store.clear()                                       // логин и пароль
        cache.clear()                                       // сохранённый список оценок
        try { repo.clearAll() } catch (e: Exception) { Log.e(TAG, "Не удалось стереть историю: ${e.message}") }
        CookieManager.getInstance().removeAllCookies(null)  // сессия портала
        WebStorage.getInstance().deleteAllData()            // данные страниц портала
        webView.clearCache(true)
        webView.clearHistory()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun showUpdateBanner() {
        val banner = findViewById<LinearLayout>(R.id.updateBanner)
        val info = updater.pendingUpdate()
        if (info == null) {
            banner.visibility = View.GONE
            return
        }
        banner.visibility = View.VISIBLE
        findViewById<TextView>(R.id.tvUpdate).text = "Доступна версия ${info.versionName}. Нажми, чтобы скачать"
        banner.setOnClickListener { openUrl(info.apkUrl) }
        findViewById<Button>(R.id.btnUpdateDismiss).setOnClickListener {
            updater.dismiss(info)
            banner.visibility = View.GONE
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Не нашлось приложения, чтобы открыть ссылку", Toast.LENGTH_LONG).show()
        }
    }

    private fun startRefresh() {
        cache.lastAttemptAt = System.currentTimeMillis() // фиксируем ПОПЫТКУ до запроса
        statusState = "Загрузка..."
        publishState()
        Log.d(TAG, "Переход на стартовую страницу")
        webView.loadUrl("https://agenda.sime.md/")
    }

    private fun formatTime(millis: Long): String =
        SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(millis))

    inner class WebAppInterface {
        @JavascriptInterface
        fun onTimeout() {
            val last = cache.lastSuccessAt
            val suffix = if (last > 0) ". Показаны данные от ${formatTime(last)}" else ""
            runOnUiThread {
                statusState = "Не удалось войти (${accountTypeValue.label.lowercase()}). Проверь логин, пароль и тип входа. Сменить их можно в разделе «Аккаунт»$suffix"
                publishState()
            }
        }

        @JavascriptInterface
        fun processHTML(html: String) {
            Log.d(TAG, "HTML успешно получен в Kotlin. Размер: ${html.length} символов")

            // 1. Чистая логика: HTML -> список предметов (без Android-зависимостей)
            val result = GradesParser.parse(html)
            result.forEach {
                Log.d(TAG, "[${it.semester}] '${it.subject}': ${it.scores}, media='${it.media}'")
            }

            if (result.isEmpty()) {
                Log.d(TAG, "Таблицы report_table_sem* не найдены или пусты.")
                runOnUiThread {
                    statusState = "Таблица оценок не найдена"
                    publishState()
                }
                return
            }

            // 3. Локальная история: сравниваем со снимком в базе, записываем разницу и проставляем даты.
            //    recent = блок "Note recente" с настоящими датами оценок.
            val recent = RecentEvalParser.parse(html)
            Log.d(TAG, "Свежих оценок с датами на странице: ${recent.size}")
            val report = try {
                repo.sync(result, recent)
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка записи истории: ${e.message}")
                null
            }

            // Показываем список вместе с датами из базы (если база не сработала, то без дат)
            val display = report?.display ?: result

            // Кэш: именно его увидим при следующем открытии и в разделе «Аналитика»
            cache.save(display, System.currentTimeMillis())

            // 4. Побочный эффект: интерфейс (только из главного потока)
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            val details = when {
                report == null -> "история не записана"
                report.isBaseline -> "история оценок начата"
                else -> describeChanges(report.changes)
            }
            val hasNew = report?.changes?.any { it.type == ChangeType.ADDED } == true

            runOnUiThread {
                gradesState = display
                statusState = "Обновлено в $time · $details"
                publishState()
                if (hasNew) {
                    Toast.makeText(this@MainActivity, "Появились новые оценки!", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /** Короткое описание изменений для строки статуса. */
    private fun describeChanges(changes: List<GradeChange>): String {
        val added = changes.filter { it.type == ChangeType.ADDED }
        val modified = changes.count { it.type == ChangeType.MODIFIED }
        val removed = changes.count { it.type == ChangeType.REMOVED }

        val parts = mutableListOf<String>()
        if (added.isNotEmpty()) {
            val shown = added.take(3).joinToString(", ") { "${it.subject}: ${it.newScore}" }
            val rest = added.size - 3
            parts.add("новые оценки: $shown" + if (rest > 0) " и ещё $rest" else "")
        }
        if (modified > 0) parts.add("исправлено: $modified")
        if (removed > 0) parts.add("удалено: $removed")
        return if (parts.isEmpty()) "новых оценок нет" else parts.joinToString(" · ")
    }

    private companion object {
        const val KEY_NAV = "nav_id"
    }
}
