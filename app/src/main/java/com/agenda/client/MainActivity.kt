package com.agenda.client

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.*
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var tvWelcome: TextView
    private lateinit var tvStatus: TextView
    private lateinit var rvGrades: RecyclerView
    private val adapter = SubjectAdapter()
    private lateinit var repo: GradesRepository
    private lateinit var cache: SnapshotCache
    private lateinit var updater: UpdateChecker

    private var usernameIntent: String = ""
    private var passwordIntent: String = ""

    private val client = OkHttpClient()
    private val TAG = "AGENDA_DEBUG"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Логин и пароль берём из защищённого хранилища, а не из Intent
        val store = CredentialStore(this)
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

        setContentView(R.layout.activity_main)

        tvWelcome = findViewById(R.id.tvWelcome)
        tvStatus = findViewById(R.id.tvStatus)
        rvGrades = findViewById(R.id.rvGrades)
        webView = findViewById(R.id.webView)
        val btnRefresh = findViewById<Button>(R.id.btnRefresh)
        val btnLogout = findViewById<Button>(R.id.btnLogout)
        val btnAnalytics = findViewById<Button>(R.id.btnAnalytics)

        rvGrades.layoutManager = LinearLayoutManager(this)
        rvGrades.adapter = adapter

        tvWelcome.text = "Привет, $usernameIntent!"

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
                                    var parentImg = document.querySelector('img[alt="Părinte"]');
                                    var parentLink = parentImg && parentImg.closest('a');
                                    if (parentLink) {
                                        window._parentClicked = true;
                                        console.log("Кликаем по Părinte");
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

        btnRefresh.setOnClickListener {
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

        btnAnalytics.setOnClickListener {
            startActivity(Intent(this, AnalyticsActivity::class.java))
        }

        btnLogout.setOnClickListener {
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

        // Сначала показываем сохранённое: экран не пустой, портал пока не трогаем
        val cached = cache.load()
        if (cached.isNotEmpty()) {
            adapter.submit(cached)
            tvStatus.text = "Данные от ${formatTime(cache.lastSuccessAt)}"
        }

        // На портал идём только если данные устарели
        if (RefreshPolicy.shouldAutoRefresh(cache.lastAttemptAt, System.currentTimeMillis())) {
            startRefresh()
        }

        // Баннер "доступна новая версия": сначала из сохранённого, потом (раз в сутки) проверяем сервер
        showUpdateBanner()
        updater.refreshIfDue { runOnUiThread { showUpdateBanner() } }
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
        tvStatus.text = "Загрузка..."
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
                tvStatus.text = "Не удалось войти. Проверь логин и пароль или нажми «Сменить аккаунт»$suffix"
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
                runOnUiThread { tvStatus.text = "Таблица оценок не найдена" }
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

            // Кэш: именно его увидим при следующем открытии и на экране аналитики
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
                adapter.submit(display)
                tvStatus.text = "Обновлено в $time · $details"
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
}
