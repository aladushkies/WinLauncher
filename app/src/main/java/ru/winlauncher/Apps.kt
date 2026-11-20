package ru.winlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import java.io.File

// ---------- Блокнот ----------
fun notesApp(c: Context): View = EditText(c).apply {
    val p = c.getSharedPreferences("notes", 0)
    setText(p.getString("t", ""))
    hint = "Заметки…"
    gravity = Gravity.TOP or Gravity.START
    setBackgroundColor(0xFFFFFFF4.toInt()); setTextColor(Color.BLACK); setHintTextColor(Color.GRAY)
    setPadding(c.dp(10), c.dp(8), c.dp(10), c.dp(8))
    addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(s: Editable?) { p.edit().putString("t", s.toString()).apply() }
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
    })
}

// ---------- Калькулятор ----------
fun calcApp(c: Context): View {
    val root = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFF202020.toInt()) }
    val disp = TextView(c).apply {
        text = "0"; textSize = 32f; setTextColor(Color.WHITE)
        gravity = Gravity.END or Gravity.CENTER_VERTICAL; setPadding(c.dp(12), c.dp(8), c.dp(12), c.dp(8))
    }
    root.addView(disp, LinearLayout.LayoutParams(MATCH_PARENT, c.dp(80)))

    var acc = 0.0; var op: Char? = null; var cur = ""; var fresh = true
    fun num() = cur.toDoubleOrNull() ?: 0.0
    fun fmt(d: Double) = when {
        !d.isFinite() -> "Ошибка"
        d == Math.floor(d) && Math.abs(d) < 1e12 -> d.toLong().toString()
        else -> d.toString()
    }
    fun calc(a: Double, o: Char, b: Double) = when (o) { '+' -> a + b; '-' -> a - b; '×' -> a * b; else -> a / b }

    fun press(k: String) {
        when (k) {
            "C" -> { acc = 0.0; op = null; cur = ""; fresh = true; disp.text = "0" }
            "+", "-", "×", "÷" -> {
                if (cur.isNotEmpty()) { acc = if (op == null) num() else calc(acc, op!!, num()); cur = "" }
                op = k[0]; fresh = false; disp.text = fmt(acc)
            }
            "=" -> if (op != null && cur.isNotEmpty()) {
                acc = calc(acc, op!!, num()); op = null; cur = fmt(acc); fresh = true; disp.text = cur
            }
            else -> {
                if (fresh) { cur = ""; fresh = false }
                if (k == "." && cur.contains('.')) return
                cur += k; disp.text = cur
            }
        }
    }

    val rows = listOf(
        listOf("7", "8", "9", "÷"), listOf("4", "5", "6", "×"),
        listOf("1", "2", "3", "-"), listOf("0", ".", "=", "+"), listOf("C")
    )
    for (r in rows) {
        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        for (k in r) {
            val b = Button(c).apply { text = k; textSize = 20f; setOnClickListener { press(k) } }
            row.addView(b, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f))
        }
        root.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
    }
    return root
}

// ---------- Браузер ----------
class BrowserView(c: Context) : LinearLayout(c) {
    private val web = WebView(c)
    private val url = EditText(c)

    init {
        orientation = VERTICAL
        url.apply {
            setSingleLine(); imeOptions = EditorInfo.IME_ACTION_GO
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            hint = "Адрес или поисковый запрос"
            setTextColor(Color.BLACK); setHintTextColor(Color.GRAY); setBackgroundColor(0xFFEFEFEF.toInt())
            setPadding(c.dp(10), c.dp(8), c.dp(10), c.dp(8))
            setOnEditorActionListener { _, _, _ -> go(text.toString()); true }
        }
        web.settings.javaScriptEnabled = true
        web.webViewClient = object : WebViewClient() {
            override fun onPageStarted(v: WebView, u: String, f: Bitmap?) { url.setText(u) }
        }
        addView(url, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        addView(web, LayoutParams(MATCH_PARENT, 0, 1f))
        go("https://duckduckgo.com")
    }

    private fun go(q: String) {
        val s = q.trim()
        if (s.isEmpty()) return
        val u = when {
            s.startsWith("http://") || s.startsWith("https://") -> s
            s.contains(' ') || !s.contains('.') -> "https://duckduckgo.com/?q=" + Uri.encode(s)
            else -> "https://$s"
        }
        web.loadUrl(u)
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(url.windowToken, 0)
    }

    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); web.destroy() }
}

// ---------- Проводник ----------
class FilesView(c: Context) : LinearLayout(c) {
    private var dir: File = Environment.getExternalStorageDirectory()
    private val path = TextView(c)
    private val list = ListView(c)
    private var items: List<File> = emptyList()

    init {
        orientation = VERTICAL
        setBackgroundColor(0xFF2B2B2B.toInt())
        path.setPadding(c.dp(10), c.dp(10), c.dp(10), c.dp(10))
        path.setBackgroundColor(0xFF3C3C3C.toInt()); path.setTextColor(Color.WHITE)
        path.setOnClickListener { dir.parentFile?.let { dir = it; refresh() } }   // тап по пути = «вверх»
        list.setOnItemClickListener { _, _, pos, _ ->
            val f = items.getOrNull(pos)
            if (f != null && f.isDirectory) { dir = f; refresh() }
        }
        addView(path, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        addView(list, LayoutParams(MATCH_PARENT, 0, 1f))
        refresh()
    }

    private fun refresh() {
        path.text = "⬆  " + dir.absolutePath
        items = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
        val names = if (items.isEmpty()) listOf("(пусто или нет доступа)")
        else items.map { (if (it.isDirectory) "📁 " else "📄 ") + it.name }
        list.adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, names)
    }
}

// ---------- Часы ----------
fun clockApp(c: Context): View {
    val root = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setBackgroundColor(0xFF101820.toInt())
    }
    val time = TextClock(c).apply {
        format24Hour = "HH:mm:ss"; format12Hour = "HH:mm:ss"; textSize = 56f; setTextColor(Color.WHITE)
    }
    val date = TextClock(c).apply {
        format24Hour = "EEEE, d MMMM yyyy"; format12Hour = "EEEE, d MMMM yyyy"; textSize = 18f; setTextColor(0xFFAAAAAA.toInt())
    }
    root.addView(time, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
    root.addView(date, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
    return root
}
