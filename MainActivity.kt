package com.keralacalendar2027

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class CalendarEvent(
    val id: Long,
    val title: String,
    val start: LocalDate,
    val end: LocalDate,
    val notes: String = "",
    val category: String = "Personal"
) {
    fun includes(date: LocalDate) = !date.isBefore(start) && !date.isAfter(end)
    fun durationText(): String = if (start == end) {
        start.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
    } else {
        "${start.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))} – ${end.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))}"
    }
}

private data class CalendarDayInfo(
    val date: LocalDate,
    val printedRed: Boolean,
    val sakaDay: Int?,
    val hijriDay: Int?,
    val malayalamDay: Int?,
    val almanacText: String,
    val eventText: String,
    val cellText: String
)

private data class CalendarMonthInfo(
    val month: Int,
    val englishName: String,
    val malayalamName: String,
    val headerNotes: String,
    val bankHolidayText: String,
    val rawPageText: String,
    val days: Map<Int, CalendarDayInfo>
)

class MainActivity : Activity() {
    private lateinit var frame: FrameLayout
    private var displayedMonth = YearMonth.of(2027, 1)
    private val events = mutableListOf<CalendarEvent>()
    private val calendarMonths = mutableMapOf<Int, CalendarMonthInfo>()
    private var pdfRenderer: PdfRenderer? = null
    private var pdfDescriptor: ParcelFileDescriptor? = null
    private var pdfPage = 0
    private val dayFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)
    private val shortFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        frame = findViewById(R.id.contentFrame)
        loadCalendarData()
        loadEvents()
        findViewById<Button>(R.id.tabCalendar).setOnClickListener { showCalendar() }
        findViewById<Button>(R.id.tabEvents).setOnClickListener { showEvents() }
        findViewById<Button>(R.id.tabPdf).setOnClickListener { showPdf() }
        showCalendar()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(
        text: String,
        size: Float = 16f,
        bold: Boolean = false,
        color: Int = Color.rgb(32, 53, 47)
    ): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(8), dp(7), dp(8), dp(7))
        setLineSpacing(dp(2).toFloat(), 1f)
    }

    private fun button(text: String, action: () -> Unit): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        minHeight = dp(46)
        setOnClickListener { action() }
    }

    private fun baseScroll(): Pair<ScrollView, LinearLayout> {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            contentDescription = "Scrollable calendar content"
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(24))
        }
        scroll.addView(col)
        frame.removeAllViews()
        frame.addView(scroll)
        return scroll to col
    }

    private fun loadCalendarData() {
        try {
            val json = assets.open("calendar_2027.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(json)
            val months = root.getJSONArray("months")
            for (i in 0 until months.length()) {
                val obj = months.getJSONObject(i)
                val monthNumber = obj.getInt("month")
                val daysArray = obj.getJSONArray("days")
                val days = mutableMapOf<Int, CalendarDayInfo>()
                for (j in 0 until daysArray.length()) {
                    val d = daysArray.getJSONObject(j)
                    val date = LocalDate.parse(d.getString("date"))
                    days[date.dayOfMonth] = CalendarDayInfo(
                        date = date,
                        printedRed = d.optBoolean("printedRed", false),
                        sakaDay = nullableInt(d, "sakaDay"),
                        hijriDay = nullableInt(d, "hijriDay"),
                        malayalamDay = nullableInt(d, "malayalamDay"),
                        almanacText = d.optString("almanacText", "").trim(),
                        eventText = d.optString("eventText", "").trim(),
                        cellText = d.optString("cellText", "").trim()
                    )
                }
                calendarMonths[monthNumber] = CalendarMonthInfo(
                    month = monthNumber,
                    englishName = obj.optString("englishName", ""),
                    malayalamName = obj.optString("malayalamName", ""),
                    headerNotes = obj.optString("headerNotes", "").trim(),
                    bankHolidayText = obj.optString("bankHolidayText", "").trim(),
                    rawPageText = obj.optString("rawPageText", ""),
                    days = days
                )
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Calendar details could not be loaded: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun nullableInt(obj: JSONObject, key: String): Int? =
        if (obj.isNull(key)) null else obj.optInt(key)

    private fun dayInfo(date: LocalDate): CalendarDayInfo? =
        if (date.year == 2027) calendarMonths[date.monthValue]?.days?.get(date.dayOfMonth) else null

    private fun showCalendar() {
        val (_, col) = baseScroll()

        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        heading.addView(
            button("‹") {
                if (displayedMonth.year > 2027 || displayedMonth.monthValue > 1) {
                    displayedMonth = displayedMonth.minusMonths(1)
                    showCalendar()
                }
            }, LinearLayout.LayoutParams(dp(54), dp(48))
        )
        heading.addView(
            label("${displayedMonth.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)} ${displayedMonth.year}", 20f, true)
                .apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        heading.addView(
            button("›") {
                if (displayedMonth.year < 2027 || displayedMonth.monthValue < 12) {
                    displayedMonth = displayedMonth.plusMonths(1)
                    showCalendar()
                }
            }, LinearLayout.LayoutParams(dp(54), dp(48))
        )
        col.addView(heading)
        col.addView(label("Tap a date for its Malayalam, Saka and Hijri date, nakshatra, tithi, observances and your events.", 14f))

        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val searchInput = EditText(this).apply {
            hint = "Search festivals, nakshatras, holidays…"
            singleLine = true
            textSize = 15f
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(dp(10), dp(4), dp(10), dp(4))
            contentDescription = "Search the 2027 Kerala calendar"
        }
        searchRow.addView(searchInput, LinearLayout.LayoutParams(0, dp(52), 1f))
        searchRow.addView(button("Search") { searchCalendar(searchInput.text.toString()) },
            LinearLayout.LayoutParams(dp(104), dp(50)))
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchCalendar(searchInput.text.toString())
                true
            } else false
        }
        col.addView(searchRow)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        actions.addView(button("＋ Add event / vacation") { createEvent() },
            LinearLayout.LayoutParams(0, dp(50), 1.15f))
        actions.addView(button("Month details") { showMonthDetails(displayedMonth.monthValue) },
            LinearLayout.LayoutParams(0, dp(50), 0.85f))
        col.addView(actions)
        col.addView(label("Small number = Malayalam date. Red = marked as a public holiday in the supplied PDF. • = almanac details available.", 12f, false, 0xFF52635D.toInt()))

        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val names = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val weekHead = LinearLayout(this)
        names.forEach { name ->
            weekHead.addView(
                label(name, 12f, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(7), 0, dp(7)) },
                LinearLayout.LayoutParams(0, dp(38), 1f)
            )
        }
        grid.addView(weekHead)

        val firstOffset = displayedMonth.atDay(1).dayOfWeek.value % 7
        val daysInMonth = displayedMonth.lengthOfMonth()
        var day = 1
        repeat(6) { week ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            repeat(7) { dow ->
                val index = week * 7 + dow
                val cell = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(1), dp(3), dp(1), dp(3))
                    setBackgroundColor(0xFFFFFFFF.toInt())
                    isFocusable = true
                }
                if (index >= firstOffset && day <= daysInMonth) {
                    val date = displayedMonth.atDay(day)
                    val info = dayInfo(date)
                    val personalCount = events.count { it.includes(date) }
                    val hasSourceDetails = info != null && (info.eventText.isNotBlank() || info.almanacText.isNotBlank())
                    val dayColor = if (info?.printedRed == true) 0xFFE0314A.toInt() else 0xFF20352F.toInt()
                    cell.addView(label(day.toString(), 18f, true, dayColor).apply {
                        gravity = Gravity.CENTER
                        setPadding(0, dp(3), 0, 0)
                    })
                    val localDay = info?.malayalamDay?.toString() ?: " "
                    cell.addView(label(if (localDay.isNotBlank()) localDay else " ", 10f, false, 0xFF60736B.toInt()).apply {
                        gravity = Gravity.CENTER
                        setPadding(0, 0, 0, 0)
                        contentDescription = if (localDay.isNotBlank()) "Malayalam date $localDay" else ""
                    })
                    val marker = buildString {
                        if (hasSourceDetails) append("●")
                        if (personalCount > 0) append("  ${personalCount}★")
                    }
                    cell.addView(label(marker, 9f, true, 0xFF16805D.toInt()).apply {
                        gravity = Gravity.CENTER
                        setPadding(0, 0, 0, 0)
                        contentDescription = when {
                            personalCount > 0 && hasSourceDetails -> "Calendar details and $personalCount personal events"
                            personalCount > 0 -> "$personalCount personal events"
                            hasSourceDetails -> "Calendar details available"
                            else -> "No saved event"
                        }
                    })
                    cell.contentDescription = buildString {
                        append(date.format(dayFormatter))
                        info?.malayalamDay?.let { append(", Malayalam date $it") }
                        if (info?.printedRed == true) append(", marked red in source calendar")
                        if (hasSourceDetails) append(", calendar details available")
                        if (personalCount > 0) append(", $personalCount personal events")
                    }
                    cell.setOnClickListener { showDay(date) }
                    day++
                } else {
                    cell.addView(label(" ", 18f))
                    cell.addView(label(" ", 10f))
                    cell.addView(label(" ", 9f))
                }
                row.addView(cell, LinearLayout.LayoutParams(0, dp(72), 1f).apply {
                    setMargins(dp(2), dp(2), dp(2), dp(2))
                })
            }
            grid.addView(row)
        }
        col.addView(grid)

        col.addView(label("Your upcoming events", 18f, true))
        val upcoming = events.filter { !it.end.isBefore(LocalDate.of(2027, 1, 1)) && !it.start.isAfter(LocalDate.of(2027, 12, 31)) }
            .sortedBy { it.start }.take(4)
        if (upcoming.isEmpty()) col.addView(label("No personal events overlap 2027 yet.", 14f))
        upcoming.forEach { col.addView(eventCard(it)) }
    }

    private fun showDay(date: LocalDate) {
        val info = dayInfo(date)
        val body = StringBuilder()
        if (info != null) {
            val localDate = info.malayalamDay?.toString() ?: "Not legible in the source cell"
            body.append("Malayalam date: ").append(localDate).append('\n')
            info.sakaDay?.let { body.append("Saka date: ").append(it).append('\n') }
            info.hijriDay?.let { body.append("Hijri date: ").append(it).append('\n') }
            if (info.printedRed) body.append("\nMarked in red as a public holiday in the supplied calendar.\n")
            if (info.almanacText.isNotBlank()) {
                body.append("\nNakshatra, tithi and day marking (as printed):\n")
                    .append(info.almanacText).append('\n')
            }
            if (info.eventText.isNotBlank()) {
                body.append("\nCalendar observances and muhurtham details:\n")
                    .append(info.eventText).append('\n')
            }
        } else {
            body.append("The printed Kerala Calendar 2027 does not contain native almanac data for this date.\n")
        }
        val personal = events.filter { it.includes(date) }
        if (personal.isNotEmpty()) {
            body.append("\nYour events:\n")
            personal.forEach { e ->
                body.append("• ").append(e.title).append(" ( ").append(e.category).append(" )")
                if (e.notes.isNotBlank()) body.append("\n  ").append(e.notes)
                body.append('\n')
            }
        } else {
            body.append("\nNo personal events saved for this date.")
        }
        AlertDialog.Builder(this)
            .setTitle(date.format(dayFormatter))
            .setMessage(body.toString())
            .setPositiveButton("Add event") { _, _ -> createEvent(date) }
            .setNeutralButton("Month details") { _, _ -> showMonthDetails(date.monthValue) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showMonthDetails(monthNumber: Int) {
        val month = calendarMonths[monthNumber] ?: return
        val content = StringBuilder()
        content.append(month.englishName).append(" 2027 · ").append(month.malayalamName).append("\n\n")
        content.append("MONTHLY ALMANAC AND TIMINGS\n")
        content.append(month.headerNotes.ifBlank { "No separate monthly notes were extracted for this page." })
        if (month.bankHolidayText.isNotBlank()) {
            content.append("\n\nBANK HOLIDAY REFERENCES IN THE SOURCE\n")
            content.append(month.bankHolidayText)
        }
        content.append("\n\nDAY-BY-DAY OBSERVANCES AND MUHURTHAM\n")
        month.days.values.sortedBy { it.date }.forEach { d ->
            content.append("\n").append(d.date.dayOfMonth.toString().padStart(2, '0'))
                .append("  ").append(shortFormatter.format(d.date))
            d.malayalamDay?.let { content.append("  ·  Malayalam date ").append(it) }
            d.sakaDay?.let { content.append("  ·  Saka ").append(it) }
            d.hijriDay?.let { content.append("  ·  Hijri ").append(it) }
            if (d.printedRed) content.append("  ·  Public holiday marking")
            content.append("\n")
            if (d.almanacText.isNotBlank()) {
                content.append("Nakshatra / tithi / day marking: ").append(d.almanacText.replace('\n', ' ')).append("\n")
            }
            if (d.eventText.isNotBlank()) {
                content.append(d.eventText).append("\n")
            }
        }
        content.append("\nAlmanac timings are reproduced from the supplied PDF. The PDF states that nakshatra timings are end times (IST) from Kerala sunrise.")
        val scroll = ScrollView(this)
        val text = TextView(this).apply {
            this.text = content.toString()
            textSize = 15f
            setTextColor(0xFF20352F.toInt())
            setPadding(dp(18), dp(14), dp(18), dp(20))
            setLineSpacing(dp(3).toFloat(), 1f)
            setTextIsSelectable(true)
            contentDescription = "Full month almanac, timings, holidays and observances for ${month.englishName} 2027"
        }
        scroll.addView(text)
        AlertDialog.Builder(this)
            .setTitle("${month.englishName} 2027 · ${month.malayalamName}")
            .setView(scroll)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun searchCalendar(query: String) {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isBlank()) {
            Toast.makeText(this, "Enter a festival, holiday, nakshatra or keyword to search.", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()
        for (monthNumber in 1..12) {
            val month = calendarMonths[monthNumber] ?: continue
            val resultsBeforeMonth = labels.size
            for (day in month.days.values.sortedBy { it.date }) {
                val haystack = "${day.eventText}\n${day.almanacText}\n${day.cellText}".lowercase(Locale.ROOT)
                if (haystack.contains(q)) {
                    val detail = (day.eventText.ifBlank { day.almanacText }).replace('\n', ' ').take(90)
                    labels.add("${shortFormatter.format(day.date)} — ${if (detail.isBlank()) "Almanac details" else detail}")
                    actions.add { displayedMonth = YearMonth.of(2027, monthNumber); showCalendar(); showDay(day.date) }
                }
            }
            val monthHaystack = "${month.headerNotes}\n${month.bankHolidayText}\n${month.rawPageText}".lowercase(Locale.ROOT)
            val hasDayResult = labels.size > resultsBeforeMonth
            if (!hasDayResult && monthHaystack.contains(q)) {
                labels.add("${month.englishName} 2027 — monthly notes and almanac")
                actions.add { displayedMonth = YearMonth.of(2027, monthNumber); showCalendar(); showMonthDetails(monthNumber) }
            }
        }
        if (labels.isEmpty()) {
            AlertDialog.Builder(this).setTitle("No calendar matches")
                .setMessage("No match was found in the extracted 2027 calendar text. Try another spelling or search in Malayalam.")
                .setPositiveButton("OK", null).show()
            return
        }
        val selectedActions = actions.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Search results (${labels.size})")
            .setItems(labels.toTypedArray()) { _, which -> selectedActions[which].invoke() }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun createEvent(prefill: LocalDate? = null) {
        val titleInput = EditText(this).apply { hint = "Event name (e.g. Annual vacation)" }
        val notesInput = EditText(this).apply { hint = "Notes (optional)" }
        val category = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Personal", "Vacation / Leave", "Travel", "Family", "Work", "Other")
            )
        }
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(titleInput)
            addView(category)
            addView(notesInput)
        }
        var start = prefill ?: LocalDate.now()
        fun chooseDate(current: LocalDate, onChosen: (LocalDate) -> Unit) {
            DatePickerDialog(
                this,
                { _, y, m, d -> onChosen(LocalDate.of(y, m + 1, d)) },
                current.year, current.monthValue - 1, current.dayOfMonth
            ).show()
        }
        AlertDialog.Builder(this).setTitle("Create date-range event").setView(wrap)
            .setNeutralButton("Choose start date", null)
            .setPositiveButton("Continue", null)
            .setNegativeButton("Cancel", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                        chooseDate(start) {
                            start = it
                            Toast.makeText(this, "Start date: ${start.format(shortFormatter)}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val title = titleInput.text.toString().trim()
                        if (title.isEmpty()) {
                            titleInput.error = "Please enter an event name"
                            return@setOnClickListener
                        }
                        chooseDate(start) { chosenEnd ->
                            var finalStart = start
                            var finalEnd = chosenEnd
                            if (finalEnd.isBefore(finalStart)) {
                                val old = finalStart
                                finalStart = finalEnd
                                finalEnd = old
                                Toast.makeText(this, "The dates were reversed; the range has been corrected.", Toast.LENGTH_LONG).show()
                            }
                            events.add(
                                CalendarEvent(
                                    System.currentTimeMillis(), title, finalStart, finalEnd,
                                    notesInput.text.toString().trim(), category.selectedItem.toString()
                                )
                            )
                            saveEvents()
                            dialog.dismiss()
                            showCalendar()
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun eventCard(e: CalendarEvent): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        box.addView(label(e.title, 16f, true))
        box.addView(label("${e.category}  •  ${e.durationText()}", 13f))
        if (e.notes.isNotBlank()) box.addView(label(e.notes, 14f))
        box.addView(button("Delete event") {
            AlertDialog.Builder(this).setMessage("Delete ‘${e.title}’?")
                .setPositiveButton("Delete") { _, _ ->
                    events.removeAll { it.id == e.id }
                    saveEvents()
                    showEvents()
                }
                .setNegativeButton("Cancel", null).show()
        })
        return box
    }

    private fun showEvents() {
        val (_, col) = baseScroll()
        col.addView(label("My Events", 22f, true))
        col.addView(label("Create events across a range of dates. Both the first and last dates are included. Events are stored only on this device.", 14f))
        col.addView(button("＋ Add event / vacation") { createEvent() })
        if (events.isEmpty()) {
            col.addView(label("No events added yet. Add your annual vacation, travel, family dates or work events.", 15f))
        }
        events.sortedBy { it.start }.forEach { col.addView(eventCard(it)) }
    }

    private fun showPdf() {
        val (_, col) = baseScroll()
        col.addView(label("Original Kerala Calendar 2027", 20f, true))
        col.addView(label("All 12 supplied PDF pages are included offline. The main calendar and searchable date details are also available in the Calendar tab.", 14f))
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        controls.addView(button("Previous") { if (pdfPage > 0) { pdfPage--; renderPdfPage() } })
        controls.addView(label("Page ${pdfPage + 1} / 12", 14f, true).apply { gravity = Gravity.CENTER })
        controls.addView(button("Next") { if (pdfPage < 11) { pdfPage++; renderPdfPage() } })
        col.addView(controls)
        val image = ImageView(this).apply {
            id = 1001
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Original Kerala Calendar 2027 page ${pdfPage + 1}"
        }
        col.addView(image, LinearLayout.LayoutParams(-1, dp(650)))
        renderPdfPage()
    }

    private fun renderPdfPage() {
        try {
            if (pdfRenderer == null) {
                val file = File(cacheDir, "kerala_calendar_2027.pdf")
                assets.open("kerala_calendar_2027.pdf").use { input ->
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                }
                pdfDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                pdfRenderer = PdfRenderer(pdfDescriptor!!)
            }
            val renderer = pdfRenderer ?: return
            val page = renderer.openPage(pdfPage)
            try {
                val width = resources.displayMetrics.widthPixels - dp(40)
                val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                findViewById<ImageView>(1001)?.setImageBitmap(bitmap)
                findViewById<ImageView>(1001)?.contentDescription = "Original Kerala Calendar 2027 page ${pdfPage + 1} of 12"
            } finally {
                page.close()
            }
            val root = frame.getChildAt(0) as? ScrollView
            val col = root?.getChildAt(0) as? LinearLayout
            (col?.getChildAt(2) as? LinearLayout)?.let { row ->
                (row.getChildAt(1) as? TextView)?.text = "Page ${pdfPage + 1} / 12"
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open the calendar PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveEvents() {
        val array = JSONArray()
        events.forEach { e ->
            array.put(
                JSONObject().put("id", e.id).put("title", e.title)
                    .put("start", e.start.toString()).put("end", e.end.toString())
                    .put("notes", e.notes).put("category", e.category)
            )
        }
        getSharedPreferences("calendar", MODE_PRIVATE).edit().putString("events", array.toString()).apply()
    }

    private fun loadEvents() {
        try {
            val raw = getSharedPreferences("calendar", MODE_PRIVATE).getString("events", "[]") ?: "[]"
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                events.add(
                    CalendarEvent(
                        o.getLong("id"), o.getString("title"),
                        LocalDate.parse(o.getString("start")), LocalDate.parse(o.getString("end")),
                        o.optString("notes"), o.optString("category", "Personal")
                    )
                )
            }
        } catch (_: Exception) {
            events.clear()
        }
    }

    override fun onDestroy() {
        pdfRenderer?.close()
        pdfDescriptor?.close()
        super.onDestroy()
    }
}
