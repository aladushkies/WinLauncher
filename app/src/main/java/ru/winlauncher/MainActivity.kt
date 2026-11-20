package ru.winlauncher

import android.Manifest
import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.*

class MainActivity : Activity() {

    private lateinit var layer: WindowLayer
    private lateinit var taskbarItems: LinearLayout
    private lateinit var startMenu: LinearLayout
    private lateinit var listView: ListView

    private val builtIns = listOf(
        BuiltIn("📝", "Блокнот") { notesApp(it) },
        BuiltIn("🧮", "Калькулятор") { calcApp(it) },
        BuiltIn("🌐", "Браузер") { BrowserView(it) },
        BuiltIn("📁", "Проводник") { FilesView(it) },
        BuiltIn("🕒", "Часы") { clockApp(it) }
    )
    private val all = mutableListOf<Entry>()
    private val shown = mutableListOf<Entry>()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)
        }
        setContentView(buildUi())
        loadEntries()
    }

    override fun onNewIntent(intent: Intent?) { super.onNewIntent(intent); hideMenu() }
    override fun onBackPressed() { if (startMenu.visibility == View.VISIBLE) hideMenu() /* иначе игнорируем: мы домашний экран */ }

    // ---------- UI ----------
    private fun buildUi(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val desktop = FrameLayout(this)
        desktop.setOnClickListener { hideMenu() }

        // иконки рабочего стола
        val icons = GridLayout(this).apply {
            orientation = GridLayout.VERTICAL; rowCount = 4; setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        builtIns.forEach { icons.addView(iconView(it)) }
        desktop.addView(icons, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))

        // слой окон
        layer = WindowLayer(this)
        desktop.addView(layer, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        // меню Пуск
        startMenu = buildStartMenu()
        desktop.addView(startMenu, FrameLayout.LayoutParams(dp(340), dp(460), Gravity.BOTTOM or Gravity.START))

        root.addView(desktop, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        root.addView(buildTaskbar(), LinearLayout.LayoutParams(MATCH_PARENT, dp(48)))
        layer.onChanged = { refreshTaskbar() }
        return root
    }

    private fun iconView(b: BuiltIn): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        layoutParams = ViewGroup.LayoutParams(dp(88), dp(84))
        addView(TextView(context).apply { text = b.emoji; textSize = 30f; gravity = Gravity.CENTER })
        addView(TextView(context).apply {
            text = b.title; textSize = 12f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setShadowLayer(4f, 0f, 1f, Color.BLACK)
        })
        setOnClickListener { hideMenu(); openBuiltIn(b) }
    }

    private fun buildTaskbar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setBackgroundColor(0xF01F1F1F.toInt()); gravity = Gravity.CENTER_VERTICAL
        }
        val start = TextView(this).apply {
            text = "⊞"; textSize = 26f; setTextColor(0xFF4CC2FF.toInt()); gravity = Gravity.CENTER
            setOnClickListener { toggleMenu() }
        }
        bar.addView(start, LinearLayout.LayoutParams(dp(60), MATCH_PARENT))

        taskbarItems = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(taskbarItems) }
        bar.addView(scroll, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f))

        val clock = TextClock(this).apply {
            format24Hour = "HH:mm\ndd.MM.yyyy"; format12Hour = "HH:mm\ndd.MM.yyyy"
            textSize = 12f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        }
        bar.addView(clock, LinearLayout.LayoutParams(dp(92), MATCH_PARENT))
        return bar
    }

    private fun refreshTaskbar() {
        taskbarItems.removeAllViews()
        val active = layer.active
        for (w in layer.windows) {
            val t = TextView(this).apply {
                text = "${w.emoji} ${w.title}"; textSize = 13f; setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), 0, dp(14), 0)
                alpha = if (w.minimized) 0.55f else 1f
                if (w === active) setBackgroundColor(0x55FFFFFF)
                setOnClickListener { hideMenu(); layer.toggleFromTaskbar(w) }
                setOnLongClickListener { layer.close(w); true }   // долгий тап = закрыть
            }
            taskbarItems.addView(t, LinearLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT))
        }
    }

    // ---------- Пуск ----------
    private fun buildStartMenu(): LinearLayout {
        val m = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(0xF0202020.toInt()); visibility = View.GONE
            elevation = dp(16).toFloat()
        }
        val search = EditText(this).apply {
            hint = "Поиск приложений"; setSingleLine(); setTextColor(Color.WHITE); setHintTextColor(0xFF999999.toInt())
            setBackgroundColor(0xFF3A3A3A.toInt()); setPadding(dp(12), dp(10), dp(12), dp(10))
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { applyFilter(s.toString()) }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        m.addView(search, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(dp(8), dp(8), dp(8), dp(8)) })

        listView = ListView(this).apply { divider = null }
        listView.adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(i: Int) = shown[i]
            override fun getItemId(i: Int) = i.toLong()
            override fun getView(i: Int, v: View?, p: ViewGroup?): View {
                val e = shown[i]
                return TextView(this@MainActivity).apply {
                    textSize = 15f; setTextColor(Color.WHITE); gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(14), dp(6), dp(14), dp(6)); minHeight = dp(52)
                    if (e.icon != null) {
                        e.icon.setBounds(0, 0, dp(34), dp(34))
                        setCompoundDrawables(e.icon, null, null, null); compoundDrawablePadding = dp(14); text = e.label
                    } else {
                        text = "${e.emoji}   ${e.label}"
                        setTypeface(typeface, Typeface.NORMAL)
                    }
                }
            }
        }
        listView.setOnItemClickListener { _, _, pos, _ -> hideMenu(); shown[pos].action() }
        m.addView(listView, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        return m
    }

    private fun loadEntries() {
        all.clear()
        builtIns.forEach { b -> all.add(Entry(b.title, null, b.emoji) { openBuiltIn(b) }) }
        all.add(Entry("Параметры Android", null, "⚙") { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })
        val pm = packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(main, 0)
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
            .forEach { ri ->
                val pkg = ri.activityInfo.packageName
                all.add(Entry(ri.loadLabel(pm).toString(), ri.loadIcon(pm), null) { launchExternal(pkg) })
            }
        applyFilter("")
    }

    private fun applyFilter(q: String) {
        shown.clear()
        shown.addAll(all.filter { it.label.contains(q.trim(), ignoreCase = true) })
        (listView.adapter as BaseAdapter).notifyDataSetChanged()
    }

    private fun toggleMenu() { startMenu.visibility = if (startMenu.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
    private fun hideMenu() { startMenu.visibility = View.GONE }

    // ---------- Запуск ----------
    private fun openBuiltIn(b: BuiltIn) { layer.open(b.title, b.emoji, b.create(this)) }

    /**
     * Сторонние приложения в плавающем окне (freeform).
     * Нужна включённая опция разработчика «Изменение размера в многооконном режиме» (force_resizable_activities).
     * На Android 7.x у ActivityOptions есть скрытый setLaunchStackId; freeform-стек = 2.
     */
    private fun launchExternal(pkg: String) {
        val i = packageManager.getLaunchIntentForPackage(pkg) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        val opts = ActivityOptions.makeBasic()
        var freeform = false
        try {
            ActivityOptions::class.java.getMethod("setLaunchStackId", Int::class.javaPrimitiveType).invoke(opts, 2)
            freeform = true
        } catch (_: Throwable) {}
        if (!freeform) {   // запасной вариант для других сборок (Android 8+ API)
            try {
                ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType).invoke(opts, 5)
            } catch (_: Throwable) {}
        }
        try {
            val w = (layer.width * 0.55f).toInt(); val h = (layer.height * 0.75f).toInt()
            val off = (windowCount++ % 6) * dp(28)
            opts.setLaunchBounds(Rect(dp(60) + off, dp(30) + off, dp(60) + off + w, dp(30) + off + h))
        } catch (_: Throwable) {}
        try { startActivity(i, opts.toBundle()) } catch (_: Throwable) { startActivity(i) }
    }

    private var windowCount = 0
}
