package com.example.teacherday

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.createBitmap
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    private lateinit var btnMyLessons: Button
    //private lateinit var btnExit: Button
    private lateinit var btnBells: Button
    private lateinit var recycler: RecyclerView

    private lateinit var adapter: LessonAdapter

    private val parser = ScheduleParser()

    private val REQUEST_CODE_PERMISSIONS = 100
    private val REQUEST_CODE_MANAGE_STORAGE = 101
    private var currentBitmap: Bitmap? = null

    private val TARGET_FOLDER = "MAX"
    private val TARGET_TEXT = "5б"

    private val NORMAL_CROP_WIDTH = 570
    private val NORMAL_CROP_HEIGHT = 425
    private val NORMAL_SHIFT_X = -145
    private val NORMAL_SHIFT_Y = 160

    private val SATURDAY_CROP_WIDTH = 290
    private val SATURDAY_CROP_HEIGHT = 365
    private val SATURDAY_SHIFT_X = 0
    private val SATURDAY_SHIFT_Y = 140
    private val requestStoragePermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkStoragePermissionAndParse()
    }
    private val scheduleList = listOf(
        // ПОНЕДЕЛЬНИК
        LessonItem("ПОНЕДЕЛЬНИК", "1 урок", "08.30 – 09.00", "10 минут"),
        LessonItem("ПОНЕДЕЛЬНИК", "2 урок", "09.10 – 09.50", "15 минут"),
        LessonItem("ПОНЕДЕЛЬНИК", "3 урок", "10.05 – 10.45", "15 минут"),
        LessonItem("ПОНЕДЕЛЬНИК", "4 урок", "11.00 – 11.40", "10 минут"),
        LessonItem("ПОНЕДЕЛЬНИК", "5 урок", "11.50 – 12.30", "10 минут"),
        LessonItem("ПОНЕДЕЛЬНИК", "6 урок", "12.40 – 13.20", "15 минут"),
        LessonItem("ПОНЕДЕЛЬНИК", "7 урок", "13.35 – 14.15", "10 минут"),
        LessonItem("ПОНЕДЕЛЬНИК", "8 урок", "14.25 – 15.05", ""),

        // ВТОРНИК – ПЯТНИЦА
        LessonItem("ВТОРНИК – ПЯТНИЦА", "1 урок", "08.30 – 09.10", "10 минут"),
        LessonItem("ВТОРНИК – ПЯТНИЦА", "2 урок", "09.20 – 10.00", "15 минут"),
        LessonItem("ВТОРНИК – ПЯТНИЦА", "3 урок", "10.15 – 10.55", "15 минут"),
        LessonItem("ВТОРНИК – ПЯТНИЦА", "4 урок", "11.10 – 11.50", "10 минут"),
        LessonItem("ВТОРНИК – ПЯТНИЦА", "5 урок", "12.00 – 12.40", "10 минут"),
        LessonItem("ВТОРНИК – ПЯТНИЦА", "6 урок", "12.50 – 13.30", "15 минут"),
        LessonItem("ВТОРНИК – ПЯТНИЦА", "7 урок", "13.45 – 14.25", "10 минут"),
        LessonItem("ВТОРНИК – ПЯТНИЦА", "8 урок", "14.35 – 15.15", ""),

        // СУББОТА
        LessonItem("СУББОТА", "1 урок", "08.30 – 09.10", "10 минут"),
        LessonItem("СУББОТА", "2 урок", "09.20 – 10.00", "10 минут"),
        LessonItem("СУББОТА", "3 урок", "10.10 – 10.50", "10 минут"),
        LessonItem("СУББОТА", "4 урок", "11.00 – 11.40", "10 минут"),
        LessonItem("СУББОТА", "5 урок", "11.50 – 12.30", "10 минут"),
        LessonItem("СУББОТА", "6 урок", "12.40 – 13.20", "5 минут"),
        LessonItem("СУББОТА", "7 урок", "13.25 – 14.05", "5 минут"),

        // СОКРАЩЁННЫЕ ДНИ
        LessonItem("СОКРАЩЁННЫЕ ДНИ", "1 урок", "08.30 – 09.00", "10 минут"),
        LessonItem("СОКРАЩЁННЫЕ ДНИ", "2 урок", "09.10 – 09.40", "10 минут"),
        LessonItem("СОКРАЩЁННЫЕ ДНИ", "3 урок", "09.50 – 10.20", "10 минут"),
        LessonItem("СОКРАЩЁННЫЕ ДНИ", "4 урок", "10.30 – 11.00", "5 минут"),
        LessonItem("СОКРАЩЁННЫЕ ДНИ", "5 урок", "11.05 – 11.35", "5 минут"),
        LessonItem("СОКРАЩЁННЫЕ ДНИ", "6 урок", "11.40 – 12.10", "5 минут"),
        LessonItem("СОКРАЩЁННЫЕ ДНИ", "7 урок", "12.15 – 12.45", "5 минут")
    )

    override fun onBackPressed() {
        // Закрываем активити и убиваем процесс
        finishAffinity() // закрывает все активити и убирает приложение из недавних
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        PDFBoxResourceLoader.init(applicationContext)

        findViewById<Button>(R.id.btn_screenshot).setOnClickListener {
            processPdf()
        }
        btnMyLessons = findViewById(R.id.btn_my_lessons)
        //btnExit = findViewById(R.id.btn_exit)
        btnBells = findViewById(R.id.btn_bells)
        recycler = findViewById(R.id.recyclerLessons)

        // 🔥 подключаем адаптер
        adapter = LessonAdapter()
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        btnMyLessons.setOnClickListener {
            checkStoragePermissionAndParse()
        }
        btnBells.setOnClickListener {
            showScheduleInRecyclerView()
        }

        //btnExit.setOnClickListener {
           // finishAffinity()
        //}
    }
    private fun showScheduleInRecyclerView() {
        val grouped = scheduleList.groupBy { it.day }

        val blocks = mutableListOf<LessonBlock>()

        for ((day, lessons) in grouped) {
            // Добавляем заголовок дня
            blocks.add(LessonBlock(number = 0, content = "\uD83D\uDCD4 $day")) // 📔 — иконка дня

            // Добавляем уроки этого дня
            lessons.forEach { lesson ->
                blocks.add(
                    LessonBlock(
                        number = lesson.lessonNumber.replace(Regex("\\D+"), "").toIntOrNull() ?: 0,
                        content = "${lesson.lessonNumber}: ${lesson.timeRange} (${lesson.duration})"
                    )
                )
            }

            // Добавляем пустую строку между днями (по желанию)
            blocks.add(LessonBlock(number = 0, content = ""))
        }

        // Убираем последнюю пустую строку, если она есть
        if (blocks.lastOrNull()?.content == "") {
            blocks.removeAt(blocks.lastIndex)

        }

        adapter.submitList(blocks)
    }



    private fun checkStoragePermissionAndParse() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                parseLatestSchedule()
            } else {
                requestManageStoragePermission()
            }
        } else {
            requestLegacyStoragePermission()
        }
    }

    private fun requestManageStoragePermission() {
        AlertDialog.Builder(this)
            .setTitle("Разрешение")
            .setMessage("Приложению нужен доступ к папке Загрузки/MAX.")
            .setPositiveButton("Разрешить") { _, _ ->
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                requestStoragePermission.launch(intent)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun requestLegacyStoragePermission() {
        if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            parseLatestSchedule()
        } else {
            requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 100)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() &&
            grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            parseLatestSchedule()
        } else {
            showError("Нет разрешения на чтение файлов")
        }
    }

    private fun parseLatestSchedule() {
        // 👉 показываем "загрузку" как временный список
        adapter.submitList(
            listOf(LessonBlock(0, "Загрузка расписания..."))
        )

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    parser.parseLatest()
                } catch (e: Exception) {
                    Result.Error(e.message ?: "Неизвестная ошибка")
                }
            }

            when (result) {
                is Result.Success -> displayLessons(result.lessons)
                is Result.Error -> showError(result.message)
            }
        }
    }

    private fun displayLessons(lessons: List<Lesson>) {
        if (lessons.isEmpty()) {
            adapter.submitList(
                listOf(LessonBlock(0, "Уроков на сегодня не найдено"))
            )
            return
        }

        val groupedByLesson = lessons.groupBy { it.lessonNumber }
        val maxLesson = lessons.maxOfOrNull { it.lessonNumber } ?: 1

        val blocks = mutableListOf<LessonBlock>()

        for (lessonNum in 1..maxLesson) {
            val lessonsForNum = groupedByLesson[lessonNum]

            val text = if (!lessonsForNum.isNullOrEmpty()) {
                val sorted = lessonsForNum.sortedBy { it.className }
                sorted.joinToString("\n") {
                    "${it.className} ${it.subject}"
                }
            } else {
                "Окно"
            }

            blocks.add(LessonBlock(lessonNum, text))
        }

        adapter.submitList(blocks)
    }

    private fun showError(message: String) {
        adapter.submitList(
            listOf(LessonBlock(0, "Ошибка: $message"))
        )
    }
    private fun getLatestPdfUri(): Uri? {
        try {
            // ПРИНУДИТЕЛЬНОЕ ОБНОВЛЕНИЕ MEDIASTORE
            android.util.Log.d("RASPISANIE", "🔄 Обновление MediaStore...")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                // Сканируем папку Downloads
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloadsDir.exists()) {
                    // Сканируем саму папку
                    val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                    intent.data = Uri.fromFile(downloadsDir)
                    sendBroadcast(intent)

                    // Сканируем папку MAX если существует
                    val maxDir = File(downloadsDir, TARGET_FOLDER)
                    if (maxDir.exists()) {
                        val maxIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                        maxIntent.data = Uri.fromFile(maxDir)
                        sendBroadcast(intent)

                        // Сканируем все PDF в папке MAX
                        maxDir.listFiles()?.forEach { file ->
                            if (file.extension.equals("pdf", ignoreCase = true)) {
                                val fileIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                                fileIntent.data = Uri.fromFile(file)
                                sendBroadcast(fileIntent)
                                android.util.Log.d("RASPISANIE", "   Сканирую: ${file.name}")
                            }
                        }
                    }
                }
            }

            // Даём время на обновление (500мс достаточно)
            Thread.sleep(500)

            // ТЕПЕРЬ ИЩЕМ PDF
            android.util.Log.d("RASPISANIE", "🔍 Поиск PDF в папке $TARGET_FOLDER...")

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Files.getContentUri("external")
            }

            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.DATE_ADDED
            )

            val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} = ? AND " +
                    "${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("application/pdf", "%$TARGET_FOLDER%")

            contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                val files = mutableListOf<Pair<Uri, Long>>()

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME))
                    val dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED))
                    val uri = Uri.withAppendedPath(collection, id.toString())

                    files.add(Pair(uri, dateAdded))
                    android.util.Log.d("RASPISANIE", "   📄 $name | DATE_ADDED: $dateAdded")
                }

                if (files.isNotEmpty()) {
                    val sorted = files.sortedByDescending { it.second }
                    val selected = sorted.first()
                    android.util.Log.d("RASPISANIE", "🔥 ВЫБРАН: ${selected.first}")
                    return selected.first
                } else {
                    android.util.Log.d("RASPISANIE", "❌ PDF не найдены")
                }
            }

        } catch (e: Exception) {
            android.util.Log.e("RASPISANIE", "ОШИБКА: ${e.message}")
            e.printStackTrace()
        }

        return null
    }




    private fun findTextBoundsWithPage(pdfPath: String, searchText: String): Pair<android.graphics.RectF, Int>? {
        return try {
            PDDocument.load(File(pdfPath)).use { document ->
                for (pageIndex in 0 until document.numberOfPages) {
                    val page = document.getPage(pageIndex)

                    val stripper = object : PDFTextStripper() {
                        val textPositions = mutableListOf<TextPosition>()

                        override fun writeString(text: String, textPositions: List<TextPosition>) {
                            this.textPositions.addAll(textPositions)
                        }
                    }

                    stripper.setStartPage(pageIndex + 1)
                    stripper.setEndPage(pageIndex + 1)
                    stripper.getText(document)

                    val positions = stripper.textPositions
                    val searchLen = searchText.length

                    for (i in 0 until positions.size - searchLen + 1) {
                        var match = true
                        val matchedPositions = mutableListOf<TextPosition>()

                        for (j in 0 until searchLen) {
                            val pos = positions[i + j]
                            val char = pos.unicode?.toString() ?: ""

                            if (char != searchText[j].toString()) {
                                match = false
                                break
                            }
                            matchedPositions.add(pos)
                        }

                        if (match && matchedPositions.isNotEmpty()) {
                            var minX = Float.MAX_VALUE
                            var minY = Float.MAX_VALUE
                            var maxX = Float.MIN_VALUE
                            var maxY = Float.MIN_VALUE

                            for (pos in matchedPositions) {
                                minX = min(minX, pos.x)
                                minY = min(minY, pos.y)
                                maxX = max(maxX, pos.x + pos.width)
                                maxY = max(maxY, pos.y + pos.height)
                            }

                            return Pair(android.graphics.RectF(minX, minY, maxX, maxY), pageIndex)
                        }
                    }
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun processPdf() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val latestPdfUri = getLatestPdfUri()
                if (latestPdfUri == null) {
                    showToast("📁 PDF НЕ НАЙДЕН В ПАПКЕ $TARGET_FOLDER!")
                    finish()
                    return@launch
                }

                val pdfPath = getPathFromUri(latestPdfUri)
                if (pdfPath == null) {
                    showToast("😕 НЕ УДАЛОСЬ ОТКРЫТЬ ФАЙЛ")
                    finish()
                    return@launch
                }

                val searchResult = findTextBoundsWithPage(pdfPath, TARGET_TEXT)
                if (searchResult == null) {
                    showToast("❌ ТЕКСТ '$TARGET_TEXT' НЕ НАЙДЕН!")
                    finish()
                    return@launch
                }

                val (textBounds, foundPage) = searchResult
                val isSaturday = true

                contentResolver.openFileDescriptor(latestPdfUri, "r")?.use { pfd ->
                    renderAndCropPage(pfd, textBounds, foundPage, isSaturday)
                }

                val bitmapToSave = currentBitmap
                if (bitmapToSave == null) {
                    showToast("❌ ОШИБКА СОЗДАНИЯ ИЗОБРАЖЕНИЯ")
                    finish()
                    return@launch
                }

                val fileName = "raspisanie_${System.currentTimeMillis()}.png"
                val saved = saveBitmapToDownloads(bitmapToSave, fileName)

                if (saved) {
                    val screenshotUri = getScreenshotUriByName(fileName)
                    if (screenshotUri != null) {
                        withContext(Dispatchers.Main) {
                            openScreenshot(screenshotUri)
                        }
                    } else {
                        showToast("❌ ФАЙЛ НЕ НАЙДЕН")
                        finish()
                    }
                } else {
                    showToast("❌ ОШИБКА СОХРАНЕНИЯ")
                    finish()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                showToast("💥 ОШИБКА: ${e.message}")
                finish()
            }
        }
    }

    private fun renderAndCropPage(
        pfd: ParcelFileDescriptor,
        textBounds: android.graphics.RectF,
        pageIndex: Int,
        isSaturday: Boolean
    ) {
        val pdfRenderer = PdfRenderer(pfd)
        if (pdfRenderer.pageCount > pageIndex) {
            val page = pdfRenderer.openPage(pageIndex)

            val scale = 3f
            currentBitmap = createBitmap(
                (page.width * scale).toInt(),
                (page.height * scale).toInt(),
                Bitmap.Config.ARGB_8888
            )
            currentBitmap?.eraseColor(android.graphics.Color.WHITE)

            val matrix = Matrix()
            matrix.postScale(scale, scale)
            page.render(currentBitmap!!, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            val leftPx = (textBounds.left * scale).toInt()
            val topPx = (textBounds.top * scale).toInt()
            val rightPx = (textBounds.right * scale).toInt()
            val bottomPx = (textBounds.bottom * scale).toInt()

            val cropWidth = if (isSaturday) SATURDAY_CROP_WIDTH else NORMAL_CROP_WIDTH
            val cropHeight = if (isSaturday) SATURDAY_CROP_HEIGHT else NORMAL_CROP_HEIGHT
            val shiftX = if (isSaturday) SATURDAY_SHIFT_X else NORMAL_SHIFT_X
            val shiftY = if (isSaturday) SATURDAY_SHIFT_Y else NORMAL_SHIFT_Y

            val centerX = (leftPx + rightPx) / 2 + shiftX
            val centerY = (topPx + bottomPx) / 2 + shiftY

            var cropLeft = centerX - cropWidth / 2
            var cropTop = centerY - cropHeight / 2
            var cropRight = centerX + cropWidth / 2
            var cropBottom = centerY + cropHeight / 2

            cropLeft = max(0, cropLeft)
            cropTop = max(0, cropTop)
            cropRight = min(currentBitmap!!.width, cropRight)
            cropBottom = min(currentBitmap!!.height, cropBottom)

            val cropBitmap = if (cropRight > cropLeft && cropBottom > cropTop) {
                Bitmap.createBitmap(
                    currentBitmap!!,
                    cropLeft,
                    cropTop,
                    cropRight - cropLeft,
                    cropBottom - cropTop
                )
            } else {
                currentBitmap
            }

            page.close()
            pdfRenderer.close()
            currentBitmap = cropBitmap
        } else {
            pdfRenderer.close()
        }
    }

    private fun getScreenshotUriByName(fileName: String): Uri? {
        return try {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Files.getContentUri("external")
            }

            val projection = arrayOf(MediaStore.Files.FileColumns._ID)
            val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(fileName)

            contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val id = cursor.getLong(idColumn)
                    return Uri.withAppendedPath(collection, id.toString())
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun getPathFromUri(uri: Uri): String? {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val projection = arrayOf(MediaStore.Files.FileColumns.DATA)
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val pathIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                    return cursor.getString(pathIndex)
                }
            }
            null
        } else {
            try {
                val inputStream = contentResolver.openInputStream(uri) ?: return null
                val tempFile = File(cacheDir, "temp_pdf_${System.currentTimeMillis()}.pdf")
                tempFile.outputStream().use { output ->
                    inputStream.copyTo(output)
                }
                tempFile.absolutePath
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun saveBitmapToDownloads(bitmap: Bitmap?, fileName: String): Boolean {
        if (bitmap == null) return false

        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Files.getContentUri("external")
            }

            val uri = contentResolver.insert(collection, values) ?: return false

            contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: false
        } catch (e: IOException) {
            false
        }
    }

    private fun openScreenshot(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/png")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка открытия", Toast.LENGTH_SHORT).show()
        }
    }


    private suspend fun showToast(message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        }
    }
}

// твои классы остаются
sealed class Result {
    data class Success(val lessons: List<Lesson>) : Result()
    data class Error(val message: String) : Result()
}

data class LessonBlock(
    val number: Int,
    val content: String
)