package com.greatwy8888.servicecountdown

import android.app.Activity
import android.app.DatePickerDialog
import android.app.AlertDialog
import android.content.Intent
import android.provider.DocumentsContract
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private data class Counter(
    val id: String,
    var name: String,
    var days: Int,
    var hours: Int = 0,
    var prefix: String = "",
    var deadlineMode: Boolean = false,
    var extraDays: Int = 0
)

class MainActivity : Activity() {
    private val counters = mutableListOf<Counter>()
    private val prefs by lazy { getSharedPreferences("countdown_data", MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private val cards = mutableMapOf<String, CardViews>()
    private var pendingDateField: EditText? = null
    private var pendingDateDialog: AlertDialog? = null
    private val backupPrefs by lazy { getSharedPreferences("countdown_backup", MODE_PRIVATE) }
    private val backupDir by lazy { java.io.File(filesDir, "backups").apply { if (!exists()) mkdirs() } }

    data class CardViews(
        val days: TextView, val hours: TextView, val mins: TextView,
        val secs: TextView, val status: TextView, val date: EditText?
    )

    private val tick = object : Runnable {
        override fun run() { updateAll(); handler.postDelayed(this, 1000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeFromWebsiteData()
        loadCounters()
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.settingsButton).setOnClickListener { showSettings() }
        buildCards()
        updateAll()
    }

    private fun defaultCounters() = listOf(
        Counter("webhostmost", "WebHostMost", 45),
        Counter("greatyi", "Greatyi", 90, 0, "Serv00-"),
        Counter("greatwang", "Greatwang", 90, 0, "Serv00-"),
        Counter("greatwang10", "Greatwang10", 90, 0, "Serv00-"),
        Counter("kingqi", "Kingqi", 90, 0, "Serv00-"),
        Counter("greatqi", "Greatqi", 90, 0, "Serv00-"),
        Counter("linying8781", "linying8781", 90, 0, "Serv00-"),
        Counter("y1382122", "y1382122", 90, 0, "Serv00-"),
        Counter("tied", "tied", 90, 0, "Serv00-"),
        Counter("dpdns", "Dpdns.org", 0, 0, "", true)
    )

    private fun initializeFromWebsiteData() {
        if (prefs.getBoolean("seeded_from_web", false)) return
        val editor = prefs.edit()
        try {
            val data = JSONObject(assets.open("countdown_data.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
            defaultCounters().forEach { c ->
                if (data.has(c.id)) editor.putLong(c.id + "_end", data.optLong(c.id, 0L) * 1000L)
            }
        } catch (_: Exception) { }
        editor.putBoolean("seeded_from_web", true).apply()
    }

    private fun loadCounters() {
        counters.clear()
        val saved = prefs.getString("services_json", null)
        if (saved.isNullOrBlank()) {
            counters.addAll(defaultCounters())
            // Keep the historic Dpdns date and duration values, while using a date field for future edits.
            val dpEnd = prefs.getLong("dpdns_end", 0L)
            if (dpEnd > 0L) prefs.edit().putString("dpdns_date", formatDate(dpEnd)).apply()
            saveCounterDefinitions()
            return
        }
        try {
            val arr = JSONArray(saved)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                counters.add(Counter(
                    o.optString("id"), o.optString("name"), o.optInt("days"),
                    o.optInt("hours"), o.optString("prefix"), o.optBoolean("deadlineMode"), o.optInt("extraDays")
                ))
            }
        } catch (_: Exception) {
            counters.addAll(defaultCounters())
        }
        if (counters.isEmpty()) counters.addAll(defaultCounters())
    }

    private fun saveCounterDefinitions() {
        val arr = JSONArray()
        counters.forEach { c ->
            arr.put(JSONObject().put("id", c.id).put("name", c.name).put("days", c.days)
                .put("hours", c.hours).put("prefix", c.prefix).put("deadlineMode", c.deadlineMode).put("extraDays", c.extraDays))
        }
        prefs.edit().putString("services_json", arr.toString()).apply()
    }

    override fun onResume() {
        super.onResume()
        updateAll()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    private fun buildCards() {
        val root = findViewById<LinearLayout>(R.id.container)
        root.removeAllViews()
        cards.clear()
        counters.forEach { c ->
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = getDrawable(R.drawable.bg_card)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
            }
            val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val title = TextView(this).apply {
                text = c.name; textSize = 19f
                setTextColor(Color.parseColor("#4A2B20")); setTypeface(typeface, 1)
            }
            top.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
            val badge = TextView(this).apply {
                text = if (c.deadlineMode) "截止日期" else "${c.days}天服务"
                textSize = 12f; setTextColor(Color.parseColor("#E87924"))
            }
            top.addView(badge)
            box.addView(top)

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
            }
            val ds = TextView(this); val hs = TextView(this); val ms = TextView(this); val ss = TextView(this)
            listOf(ds, hs, ms, ss).forEachIndexed { i, t ->
                t.text = "00"; t.textSize = 28f
                t.setTextColor(Color.parseColor("#4A2B20")); t.setTypeface(t.typeface, 1)
                row.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
                if (i < 3) row.addView(TextView(this).apply {
                    text = ":"; textSize = 25f; setTextColor(Color.parseColor("#D8B5A4")); gravity = Gravity.CENTER
                }, LinearLayout.LayoutParams(dp(10), -2))
            }
            box.addView(row)
            box.addView(TextView(this).apply {
                text = "      天                 时                 分                 秒"
                textSize = 10f; setTextColor(Color.parseColor("#A48678")); gravity = Gravity.CENTER
            })
            val status = TextView(this).apply {
                textSize = 13f; setTextColor(Color.parseColor("#8C6A5B"))
                gravity = Gravity.CENTER; setPadding(0, dp(5), 0, 0)
            }
            box.addView(status)

            var dateField: EditText? = null
            if (c.deadlineMode) {
                dateField = EditText(this).apply {
                    hint = "截止日期 YYYY-MM-DD"
                    setText(prefs.getString(c.id + "_date", "") ?: "")
                    inputType = 1; setSingleLine(true); textSize = 14f
                    setTextColor(Color.parseColor("#4A2B20")); gravity = Gravity.CENTER
                    isFocusable = false
                    setOnClickListener { showDatePicker(c, this) }
                }
                box.addView(dateField, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(5) })
            }

            val buttons = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
            }
            fun actionButton(label: String, listener: () -> Unit, secondary: Boolean = false): Button =
                Button(this).apply {
                    text = label; textSize = 13f; setAllCaps(false)
                    setTextColor(if (secondary) Color.parseColor("#8C6A5B") else Color.WHITE)
                    background = getDrawable(if (secondary) R.drawable.bg_button_secondary else R.drawable.bg_button)
                    setOnClickListener { listener() }
                }
            buttons.addView(actionButton("开始", { start(c) }), LinearLayout.LayoutParams(0, dp(42), 1f).apply { rightMargin = dp(3) })
            buttons.addView(actionButton("重置", { reset(c) }, true), LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(3); rightMargin = dp(3) })
            buttons.addView(actionButton("修改", { showServiceEditor(c) }, true), LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(3); rightMargin = dp(3) })
            buttons.addView(actionButton("删除", { confirmDelete(c) }, true), LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(3) })
            box.addView(buttons)
            root.addView(box)
            cards[c.id] = CardViews(ds, hs, ms, ss, status, dateField)
        }
    }

    private fun showSettings() {
        val options = arrayOf("添加服务", "导入", "导出备份", "恢复本地备份")
        AlertDialog.Builder(this).setTitle("设置").setItems(options) { _, which ->
            when (which) {
                0 -> showServiceEditor(null)
                1 -> { val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "application/json"; addCategory(Intent.CATEGORY_OPENABLE) }; startActivityForResult(i, 2001) }
                2 -> exportBackup()
                3 -> restoreLocalBackup()
            }
        }.show()
    }

    private fun showServiceEditor(existing: Counter?) {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        fun field(labelText: String, value: String = "", numeric: Boolean = false): EditText {
            // Keep the field label visible even after the user enters a value.
            val label = TextView(this).apply {
                text = labelText
                textSize = 14f
                setTextColor(Color.parseColor("#4A2B20"))
                setTypeface(typeface, 1)
                setPadding(0, dp(5), 0, 0)
            }
            panel.addView(label, LinearLayout.LayoutParams(-1, dp(24)))
            val e = EditText(this).apply {
                hint = "请输入"
                setText(value)
                textSize = 16f
                setSingleLine(true)
            }
            if (numeric) e.inputType = 2
            panel.addView(e, LinearLayout.LayoutParams(-1, dp(44)))
            return e
        }
        val name = field("服务名称", existing?.name ?: "")
        val days = field("服务时间（天）", existing?.days?.toString() ?: "90", true)
        val extraDays = field("天数", existing?.extraDays?.toString() ?: "0", true)
        val hours = field("小时", existing?.hours?.toString() ?: "0", true)
        val deadline = CheckBox(this).apply { text = "按截止日期倒计时（如 Dpdns.org）"; isChecked = existing?.deadlineMode ?: false }
        panel.addView(deadline)
        val help = TextView(this).apply {
            text = "勾选截止日期后，保存时选择具体日期；否则按天数和小时数倒计时。"
            textSize = 12f; setTextColor(Color.DKGRAY)
        }
        panel.addView(help)
        AlertDialog.Builder(this).setTitle(if (existing == null) "添加服务" else "修改服务")
            .setView(panel)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                val n = name.text.toString().trim()
                val d = days.text.toString().toIntOrNull()
                val ed = extraDays.text.toString().toIntOrNull()
                val h = hours.text.toString().toIntOrNull()
                if (n.isEmpty() || d == null || ed == null || h == null || d < 0 || ed < 0 || h < 0 || h > 999) {
                    Toast.makeText(this, "请填写有效的服务名称、服务时间、天数和小时数", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                createSafetyBackup()
                if (existing == null) {
                    val id = "custom_" + System.currentTimeMillis().toString()
                    val c = Counter(id, n, d, h, "", deadline.isChecked, ed)
                    counters.add(c)
                    if (deadline.isChecked) {
                        chooseDateForNewService(c)
                    } else {
                        prefs.edit().putLong(id + "_end", 0L).apply()
                        saveCounterDefinitions(); buildCards(); updateAll()
                    }
                } else {
                    val oldDeadlineMode = existing.deadlineMode
                    existing.name = n; existing.days = d; existing.extraDays = ed; existing.hours = h; existing.deadlineMode = deadline.isChecked
                    val editPrefs = prefs.edit()
                    if (!existing.deadlineMode) {
                        editPrefs.remove(existing.id + "_date")
                        if (prefs.getLong(existing.id + "_end", 0L) > 0L) {
                            editPrefs.putLong(existing.id + "_end",
                                System.currentTimeMillis() + d * 86400000L + h * 3600000L)
                        }
                    } else if (!oldDeadlineMode && prefs.getLong(existing.id + "_end", 0L) > 0L) {
                        editPrefs.putString(existing.id + "_date", formatDate(prefs.getLong(existing.id + "_end", 0L)))
                    }
                    editPrefs.apply()
                    saveCounterDefinitions(); buildCards(); updateAll()
                }
            }.show()
    }

    private fun chooseDateForNewService(c: Counter) {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        DatePickerDialog(this, { _, y, m, d ->
            val chosen = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            chosen.clear(); chosen.set(y, m, d, 0, 0, 0)
            val millis = chosen.timeInMillis
            prefs.edit().putString(c.id + "_date", formatDate(millis)).putLong(c.id + "_end", millis).apply()
            saveCounterDefinitions(); buildCards(); updateAll()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showDatePicker(c: Counter, field: EditText) {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val current = prefs.getLong(c.id + "_end", 0L)
        if (current > 0) cal.timeInMillis = current
        DatePickerDialog(this, { _, y, m, d ->
            val chosen = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            chosen.clear(); chosen.set(y, m, d, 0, 0, 0)
            val millis = chosen.timeInMillis
            prefs.edit().putString(c.id + "_date", formatDate(millis)).putLong(c.id + "_end", millis).apply()
            field.setText(formatDate(millis)); updateAll()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun confirmDelete(c: Counter) {
        AlertDialog.Builder(this).setTitle("删除倒计时")
            .setMessage("确定删除“${c.name}”及其倒计时吗？")
            .setNegativeButton("取消", null).setPositiveButton("删除") { _, _ ->
                createSafetyBackup()
                counters.removeAll { it.id == c.id }
                prefs.edit().remove(c.id + "_end").remove(c.id + "_date").apply()
                saveCounterDefinitions(); buildCards(); updateAll()
            }.show()
    }

    private fun start(c: Counter) {
        createSafetyBackup()
        val now = System.currentTimeMillis()
        val key = c.id + "_end"
        if (c.deadlineMode) {
            val date = prefs.getString(c.id + "_date", null)
            if (date.isNullOrBlank()) {
                val field = cards[c.id]?.date
                if (field != null) showDatePicker(c, field) else chooseDateForNewService(c)
                return
            }
        } else if (prefs.getLong(key, 0L) <= now) {
            prefs.edit().putLong(key, now + c.days * 86400000L + c.hours * 3600000L).apply()
        }
        updateAll()
    }

    private fun reset(c: Counter) {
        createSafetyBackup()
        if (c.deadlineMode) {
            val field = cards[c.id]?.date
            if (field != null) showDatePicker(c, field) else chooseDateForNewService(c)
        } else {
            prefs.edit().remove(c.id + "_end").apply()
            updateAll()
        }
    }

    private fun updateAll() {
        val now = System.currentTimeMillis()
        counters.forEach { c ->
            val v = cards[c.id] ?: return@forEach
            val end = prefs.getLong(c.id + "_end", 0L)
            if (end <= 0L) {
                set(v, 0); v.status.text = "未开始"
            } else {
                val d = end - now
                if (d <= 0L) {
                    set(v, 0); v.status.text = "倒计时已结束！"
                } else {
                    val sec = d / 1000L
                    set(v, sec / 86400L, (sec % 86400L) / 3600L, (sec % 3600L) / 60L, sec % 60L)
                    v.status.text = "正在倒计时"
                }
            }
        }
    }

    private fun set(v: CardViews, d: Long, h: Long = 0, m: Long = 0, s: Long = 0) {
        v.days.text = "%02d".format(d)
        v.hours.text = "%02d".format(h)
        v.mins.text = "%02d".format(m)
        v.secs.text = "%02d".format(s)
    }

    private fun formatDate(millis: Long): String {
        val f = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(Date(millis))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data?.data == null) return
        val uri = data.data!!
        try {
            if (requestCode == 2001) importData(uri)
            if (requestCode == 2003) {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                backupPrefs.edit().putString("folder_uri", uri.toString()).apply()
                saveExternalBackup(showToast = true)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "操作失败：${e.message ?: "文件格式错误"}", Toast.LENGTH_LONG).show()
        }
    }

    private fun backupJson(): String {
        val root = JSONObject()
        val arr = JSONArray()
        counters.forEach { c ->
            arr.put(JSONObject().put("id", c.id).put("name", c.name).put("days", c.days)
                .put("hours", c.hours).put("prefix", c.prefix).put("deadlineMode", c.deadlineMode)
                .put("extraDays", c.extraDays).put("end", prefs.getLong(c.id + "_end", 0L))
                .put("date", prefs.getString(c.id + "_date", "") ?: ""))
        }
        root.put("version", 2).put("createdAt", System.currentTimeMillis()).put("services", arr)
        return root.toString(2)
    }

    private fun timestampName(): String {
        val base = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        var name = "${base}.json"
        var suffix = 1
        while (java.io.File(backupDir, name).exists()) {
            name = "${base}_%02d.json".format(suffix)
            suffix++
        }
        return name
    }

    private fun exportBackup() {
        val folder = backupPrefs.getString("folder_uri", null)
        if (folder.isNullOrBlank()) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            startActivityForResult(intent, 2003)
        } else {
            saveExternalBackup(showToast = true)
        }
    }

    private fun saveExternalBackup(showToast: Boolean = false) {
        val raw = backupPrefs.getString("folder_uri", null)
        if (raw.isNullOrBlank()) return
        try {
            val tree = Uri.parse(raw)
            val name = timestampName()
            val fileUri = DocumentsContract.createDocument(contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)), "application/json", name)
                ?: throw IllegalStateException("无法在备份目录创建文件")
            contentResolver.openOutputStream(fileUri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(backupJson()) }
                ?: throw IllegalStateException("无法写入备份文件")
            if (showToast) Toast.makeText(this, "备份成功：${name}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            if (showToast) Toast.makeText(this, "外部备份失败：" + (e.message ?: "请检查备份目录权限"), Toast.LENGTH_LONG).show()
        }
    }

    private fun createSafetyBackup() {
        try {
            val file = java.io.File(backupDir, timestampName())
            file.writeText(backupJson(), Charsets.UTF_8)
        } catch (_: Exception) { }
        if (!backupPrefs.getString("folder_uri", null).isNullOrBlank()) saveExternalBackup(showToast = false)
    }

    private fun restoreLocalBackup() {
        val files = backupDir.listFiles()?.filter { it.isFile && it.name.endsWith(".json") }?.sortedByDescending { it.name } ?: emptyList()
        if (files.isEmpty()) {
            Toast.makeText(this, "暂无本地自动备份；请先导出备份并选择备份目录", Toast.LENGTH_LONG).show()
            return
        }
        val labels = files.map { it.name }.toTypedArray()
        AlertDialog.Builder(this).setTitle("恢复本地备份")
            .setItems(labels) { _, which ->
                AlertDialog.Builder(this).setTitle("确认恢复")
                    .setMessage("恢复 ${files[which].name} 将替换当前服务列表和倒计时，建议先导出当前数据。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("恢复") { _, _ ->
                        try {
                            val emergency = java.io.File(backupDir, timestampName())
                            emergency.writeText(backupJson(), Charsets.UTF_8)
                            importDataJson(files[which].readText(Charsets.UTF_8))
                        } catch (e: Exception) {
                            Toast.makeText(this, "恢复失败：" + (e.message ?: "备份文件无效"), Toast.LENGTH_LONG).show()
                        }
                    }.show()
            }.show()
    }

    private fun importData(uri: Uri) {
        val json = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: throw IllegalArgumentException("无法读取文件")
        createSafetyBackup()
        importDataJson(json)
    }

    private fun importDataJson(json: String) {
        val root = JSONObject(json)
        val arr = root.optJSONArray("services") ?: throw IllegalArgumentException("不是有效的倒计时备份文件")
        val imported = mutableListOf<Counter>()
        val editor = prefs.edit()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.optString("id").ifBlank { "imported_${System.currentTimeMillis()}_${i}" }
            val c = Counter(id, o.optString("name", "新服务"), o.optInt("days", 0).coerceAtLeast(0),
                o.optInt("hours", 0).coerceAtLeast(0), o.optString("prefix", ""), o.optBoolean("deadlineMode", false), o.optInt("extraDays", 0).coerceAtLeast(0))
            imported.add(c)
            editor.putLong(id + "_end", o.optLong("end", 0L))
            val date = o.optString("date", "")
            if (date.isNotBlank()) editor.putString(id + "_date", date) else editor.remove(id + "_date")
        }
        if (imported.isEmpty()) throw IllegalArgumentException("文件中没有服务")
        val importedIds = imported.map { it.id }.toSet()
        prefs.all.keys.filter { it.endsWith("_end") || it.endsWith("_date") }
            .forEach { key ->
                val id = key.removeSuffix("_end").removeSuffix("_date")
                if (id !in importedIds) editor.remove(key)
            }
        counters.clear()
        counters.addAll(imported)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = imported[i].id
            editor.putLong(id + "_end", o.optLong("end", 0L))
            val date = o.optString("date", "")
            if (date.isNotBlank()) editor.putString(id + "_date", date) else editor.remove(id + "_date")
        }
        editor.apply()
        saveCounterDefinitions(); buildCards(); updateAll()
        Toast.makeText(this, "导入成功，已按文件中的倒计时工作", Toast.LENGTH_LONG).show()
    }

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()
}
