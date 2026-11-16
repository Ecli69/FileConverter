package com.fileconverter

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

// Кастомные цвета
val BackgroundBlue = Color(0xFF0000FF)
val ButtonRoyalBlue = Color(0xFF4169E1)
val TextWhite = Color.White

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("Файл не выбран") }
    var targetFormat by remember { mutableStateOf("png") }
    var isConverting by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (it.moveToFirst() && nameIndex != -1) {
                    selectedFileName = it.getString(nameIndex)
                }
            }
            resultMessage = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlue)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Text(
            text = "File Converter",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = TextWhite
        )

        // Блок выбора файла
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Выбранный файл:", color = TextWhite, fontSize = 16.sp)
            Text(
                text = selectedFileName,
                color = TextWhite,
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { filePicker.launch("*/*") },
                shape = RoundedCornerShape(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ButtonRoyalBlue),
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Text("Выбрать файл", color = TextWhite)
            }
        }

        // Блок выбора формата
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Конвертировать в:", color = TextWhite, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))

            val formatsRow1 = listOf("png", "jpg", "webp", "gif")
            val formatsRow2 = listOf("mp4", "mp3", "wav", "flac")

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                formatsRow1.forEach { format ->
                    FormatButton(format = format, isSelected = targetFormat == format, onClick = { targetFormat = format })
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                formatsRow2.forEach { format ->
                    FormatButton(format = format, isSelected = targetFormat == format, onClick = { targetFormat = format })
                }
            }
        }

        // Статус / Результат
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
        ) {
            if (isConverting) {
                CircularProgressIndicator(color = TextWhite)
                Spacer(Modifier.height(8.dp))
                Text("Конвертация...", color = TextWhite)
            }

            if (resultMessage != null) {
                Text(
                    text = resultMessage!!,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        // Главная кнопка конвертации
        Button(
            onClick = {
                val uri = selectedFileUri
                if (uri == null) {
                    Toast.makeText(context, "Сначала выберите файл", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                isConverting = true
                resultMessage = null

                scope.launch {
                    convertFile(
                        context = context,
                        sourceUri = uri,
                        sourceFileName = selectedFileName,
                        targetFormat = targetFormat,
                        onResult = { msg ->
                            isConverting = false
                            resultMessage = msg
                        }
                    )
                }
            },
            enabled = !isConverting && selectedFileUri != null,
            shape = RoundedCornerShape(0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ButtonRoyalBlue,
                disabledContainerColor = Color.Gray
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Начать конвертацию", color = TextWhite, fontSize = 18.sp)
        }
    }
}

@Composable
fun FormatButton(format: String, isSelected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color.White else ButtonRoyalBlue,
            contentColor = if (isSelected) BackgroundBlue else TextWhite
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = Modifier.height(40.dp)
    ) {
        Text(format.uppercase(), fontWeight = FontWeight.Bold)
    }
}

// --- ЛОГИКА КОНВЕРТАЦИИ ---

suspend fun convertFile(
    context: Context,
    sourceUri: Uri,
    sourceFileName: String,
    targetFormat: String,
    onResult: (String) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            // 1. Сохраняем исходный файл в кэш
            val inputFile = File(context.cacheDir, "input_${UUID.randomUUID()}.tmp")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(inputFile).use { output ->
                    input.copyTo(output)
                }
            }

            // 2. Создаем временный выходной файл
            val tempOutputFile = File(context.cacheDir, "converted_${System.currentTimeMillis()}.$targetFormat")

            // Проверяем, является ли входной файл картинкой или аудио
            val isImageInput = sourceFileName.endsWith(".png", true) ||
                    sourceFileName.endsWith(".jpg", true) ||
                    sourceFileName.endsWith(".jpeg", true) ||
                    sourceFileName.endsWith(".webp", true)

            val isAudioInput = sourceFileName.endsWith(".mp3", true) ||
                    sourceFileName.endsWith(".wav", true) ||
                    sourceFileName.endsWith(".flac", true)

            // 3. Если нужен WebP и на входе картинка - используем встроенный кодек Android
            if (targetFormat == "webp" && isImageInput) {
                val success = convertImageToWebp(inputFile, tempOutputFile)
                if (success) {
                    val savedFileName = saveFileToDownloads(context, tempOutputFile, "webp")
                    inputFile.delete()
                    tempOutputFile.delete()

                    withContext(Dispatchers.Main) {
                        if (savedFileName != null) onResult("Успешно! Файл сохранен в Downloads:\n$savedFileName")
                        else onResult("Ошибка при сохранении в Downloads")
                    }
                    return@withContext
                }
            }

            // 4. Формируем команду FFmpeg для остальных случаев
            val command = when (targetFormat) {
                "mp4" -> "-y -i ${inputFile.absolutePath} -c:v mpeg4 -q:v 5 -c:a aac -b:a 128k ${tempOutputFile.absolutePath}"
                "mp3" -> "-y -i ${inputFile.absolutePath} -vn -ar 44100 -ac 2 -b:a 192k ${tempOutputFile.absolutePath}"
                "wav" -> "-y -i ${inputFile.absolutePath} -vn -c:a pcm_s16le ${tempOutputFile.absolutePath}"
                "flac" -> "-y -i ${inputFile.absolutePath} -vn -c:a flac ${tempOutputFile.absolutePath}"
                "gif" -> "-y -i ${inputFile.absolutePath} -vf \"fps=10,scale=480:-1:flags=lanczos\" -loop 0 ${tempOutputFile.absolutePath}"
                "webp" -> "-y -i ${inputFile.absolutePath} -c:v webp -quality 80 ${tempOutputFile.absolutePath}" // Для видео в WebP (может упасть)
                "png", "jpg" -> {
                    if (isAudioInput) {
                        // Если на входе аудио, рисуем звуковую волну
                        "-y -i ${inputFile.absolutePath} -filter_complex \"showwavespic=s=800x400:colors=white\" -frames:v 1 -update 1 ${tempOutputFile.absolutePath}"
                    } else {
                        // Если картинка или видео - берем первый кадр
                        "-y -i ${inputFile.absolutePath} -frames:v 1 -update 1 ${tempOutputFile.absolutePath}"
                    }
                }
                else -> "-y -i ${inputFile.absolutePath} -frames:v 1 -update 1 ${tempOutputFile.absolutePath}"
            }

            // 5. Выполняем
            val session = FFmpegKit.execute(command)

            if (ReturnCode.isSuccess(session.returnCode) && tempOutputFile.exists()) {
                val savedFileName = saveFileToDownloads(context, tempOutputFile, targetFormat)
                inputFile.delete()
                tempOutputFile.delete()

                withContext(Dispatchers.Main) {
                    if (savedFileName != null) onResult("Успешно! Файл сохранен в Downloads:\n$savedFileName")
                    else onResult("Ошибка при сохранении в Downloads")
                }
            } else {
                val failLog = session.failStackTrace ?: "Неизвестная ошибка"
                val shortLog = if (failLog.length > 200) failLog.take(200) + "..." else failLog
                withContext(Dispatchers.Main) {
                    onResult("Ошибка FFmpeg:\n$shortLog")
                }
            }

        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onResult("Системная ошибка:\n${e.message}")
            }
        }
    }
}

// Вспомогательная функция: кодирование картинки в WebP системным кодеком Android
fun convertImageToWebp(inputFile: File, outputFile: File): Boolean {
    return try {
        val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath)
        if (bitmap == null) return false

        val outputStream = FileOutputStream(outputFile)
        // 80 — качество (от 0 до 100)
        val success = bitmap.compress(android.graphics.Bitmap.CompressFormat.WEBP, 80, outputStream)
        outputStream.close()
        bitmap.recycle()
        success
    } catch (e: Exception) {
        false
    }
}

// Вспомогательная функция для сохранения файла в папку Downloads
fun saveFileToDownloads(context: Context, file: File, extension: String): String? {
    val fileName = "converted_${System.currentTimeMillis()}.$extension"

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }

        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                file.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            return fileName
        }
    } else {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
        val destFile = File(downloadsDir, fileName)
        file.copyTo(destFile, overwrite = true)
        return fileName
    }
    return null
}