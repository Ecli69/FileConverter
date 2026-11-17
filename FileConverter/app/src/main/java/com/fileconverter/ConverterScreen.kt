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
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.XWPFDocument
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
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Text(
            text = "File Converter",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = TextWhite
        )

        // Блок выбора файла
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Выбранный файл:", color = TextWhite, fontSize = 14.sp)
            Text(
                text = selectedFileName,
                color = TextWhite,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(8.dp))
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
            Text("Конвертировать в:", color = TextWhite, fontSize = 14.sp)
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

            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                FormatButton(format = "docx", isSelected = targetFormat == "docx", onClick = { targetFormat = "docx" })
            }
        }

        // Статус / Результат
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
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
                    fontSize = 12.sp
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
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Начать конвертацию", color = TextWhite, fontSize = 16.sp)
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
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        modifier = Modifier.height(36.dp)
    ) {
        Text(format.uppercase(), fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
            val inputFile = File(context.cacheDir, "input_${UUID.randomUUID()}.tmp")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(inputFile).use { output -> input.copyTo(output) }
            }

            val tempOutputFile = File(context.cacheDir, "converted_${System.currentTimeMillis()}.$targetFormat")

            // --- ЛОГИКА PDF -> DOCX (Извлечение текста) ---
            if (targetFormat == "docx" && sourceFileName.endsWith(".pdf", true)) {
                try {
                    val pdfDocument = PDDocument.load(inputFile)
                    val doc = XWPFDocument()
                    val text = PDFTextStripper().getText(pdfDocument)

                    val paragraph = doc.createParagraph()
                    val run = paragraph.createRun()
                    run.setText(text)

                    val outputStream = FileOutputStream(tempOutputFile)
                    doc.write(outputStream)
                    outputStream.close()
                    pdfDocument.close()
                    doc.close()

                    saveAndFinish(context, tempOutputFile, inputFile, "docx", onResult)
                    return@withContext
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { onResult("Ошибка PDF->DOCX: ${e.message}") }
                    return@withContext
                }
            }

            // --- ЛОГИКА ДЛЯ WEBP (Android Bitmap) ---
            val isImageInput = sourceFileName.endsWith(".png", true) || sourceFileName.endsWith(".jpg", true) || sourceFileName.endsWith(".jpeg", true) || sourceFileName.endsWith(".webp", true)
            if (targetFormat == "webp" && isImageInput) {
                val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath)
                if (bitmap != null) {
                    val outputStream = FileOutputStream(tempOutputFile)
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.WEBP, 80, outputStream)
                    outputStream.close()
                    bitmap.recycle()
                    saveAndFinish(context, tempOutputFile, inputFile, "webp", onResult)
                    return@withContext
                }
            }

            // --- ЛОГИКА FFMPEG (Медиа) ---
            val isAudioInput = sourceFileName.endsWith(".mp3", true) || sourceFileName.endsWith(".wav", true) || sourceFileName.endsWith(".flac", true)

            val command = when (targetFormat) {
                "mp4" -> "-y -i ${inputFile.absolutePath} -c:v mpeg4 -q:v 5 -c:a aac -b:a 128k ${tempOutputFile.absolutePath}"
                "mp3" -> "-y -i ${inputFile.absolutePath} -vn -ar 44100 -ac 2 -b:a 192k ${tempOutputFile.absolutePath}"
                "wav" -> "-y -i ${inputFile.absolutePath} -vn -c:a pcm_s16le ${tempOutputFile.absolutePath}"
                "flac" -> "-y -i ${inputFile.absolutePath} -vn -c:a flac ${tempOutputFile.absolutePath}"
                "gif" -> "-y -i ${inputFile.absolutePath} -vf \"fps=10,scale=480:-1:flags=lanczos\" -loop 0 ${tempOutputFile.absolutePath}"
                "webp" -> "-y -i ${inputFile.absolutePath} -c:v webp -quality 80 ${tempOutputFile.absolutePath}"
                "png", "jpg" -> {
                    if (isAudioInput) "-y -i ${inputFile.absolutePath} -filter_complex \"showwavespic=s=800x400:colors=white\" -frames:v 1 -update 1 ${tempOutputFile.absolutePath}"
                    else "-y -i ${inputFile.absolutePath} -frames:v 1 -update 1 ${tempOutputFile.absolutePath}"
                }
                else -> "-y -i ${inputFile.absolutePath} -frames:v 1 -update 1 ${tempOutputFile.absolutePath}"
            }

            val session = FFmpegKit.execute(command)
            if (ReturnCode.isSuccess(session.returnCode) && tempOutputFile.exists()) {
                saveAndFinish(context, tempOutputFile, inputFile, targetFormat, onResult)
            } else {
                val failLog = session.failStackTrace ?: "Неизвестная ошибка"
                withContext(Dispatchers.Main) { onResult("Ошибка FFmpeg: ${failLog.take(100)}...") }
            }

        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onResult("Ошибка: ${e.message}") }
        }
    }
}

suspend fun saveAndFinish(context: Context, tempOutputFile: File, inputFile: File, ext: String, onResult: (String) -> Unit) {
    val savedFileName = saveFileToDownloads(context, tempOutputFile, ext)
    inputFile.delete()
    tempOutputFile.delete()
    withContext(Dispatchers.Main) {
        if (savedFileName != null) onResult("Успешно! Сохранено:\n$savedFileName")
        else onResult("Ошибка сохранения")
    }
}

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
                file.inputStream().use { inputStream -> inputStream.copyTo(outputStream) }
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