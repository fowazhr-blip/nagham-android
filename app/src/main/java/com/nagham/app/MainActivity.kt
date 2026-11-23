package com.nagham.app

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.ffmpeg.FFmpeg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            YoutubeDL.getInstance().init(applicationContext)
            FFmpeg.getInstance().init(applicationContext)
        } catch (_: YoutubeDLException) {
        }

        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(
                    colorScheme = darkColorScheme(
                        primary = MaterialTheme.colorScheme.primary,
                        surfaceContainerHigh = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        NaghamScreen()
                    }
                }
            }
        }
    }
}

@Composable
fun NaghamScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf("") }
    var isVideo by remember { mutableStateOf(true) }
    var isDownloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var statusMessage by remember { mutableStateOf("جاهز لتحميل المقاطع بجودة عالية") }

    val outputDir = remember {
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Nagham_Output"
        ).apply { mkdirs() }
    }

    fun pasteFromClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipText = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
        if (!clipText.isNullOrEmpty()) {
            url = clipText
            statusMessage = "تم لصق الرابط من الحافظة"
        } else {
            statusMessage = "الحافظة فارغة حالياً"
        }
    }

    fun startDownload() {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) {
            statusMessage = "يرجى إدخال رابط المقطع أولاً"
            return
        }

        isDownloading = true
        progress = 0f
        statusMessage = "جارٍ تجهيز التحميل..."

        scope.launch {
            val resultText = withContext(Dispatchers.IO) {
                try {
                    val outTemplate = File(outputDir, "%(title).60s.%(ext)s").absolutePath
                    val request = YoutubeDLRequest(cleanUrl).apply {
                        addOption("--no-playlist")
                        addOption("-o", outTemplate)
                        if (isVideo) {
                            addOption("-f", "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/bv*+ba/b")
                            addOption("--merge-output-format", "mp4")
                        } else {
                            addOption("-x")
                            addOption("--audio-format", "mp3")
                            addOption("--audio-quality", "0")
                            addOption("--embed-metadata")
                        }
                    }

                    YoutubeDL.getInstance().execute(request) { p, _, line ->
                        progress = (p / 100f).coerceIn(0f, 1f)
                        if (!line.isNullOrBlank()) {
                            statusMessage = "جارٍ التحميل... ${p.toInt()}%"
                        }
                    }
                    progress = 1f
                    "تم التحميل بنجاح في مجلد Download/Nagham_Output"
                } catch (e: Exception) {
                    "تعذّر التحميل: ${e.localizedMessage ?: "تأكد من الرابط أو الاتصال"}"
                }
            }
            statusMessage = resultText
            isDownloading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "نغم",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "تحميل المقاطع الصوتية والمرئية بسهولة",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isDownloading,
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            label = { Text("رابط المقطع (يوتيوب، يوتيوب ميوزك، تيك توك)") },
            trailingIcon = {
                IconButton(onClick = { pasteFromClipboard() }, enabled = !isDownloading) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "لصق الرابط"
                    )
                }
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilterChip(
                selected = isVideo,
                onClick = { if (!isDownloading) isVideo = true },
                modifier = Modifier.weight(1f).height(48.dp),
                label = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.VideoFile, contentDescription = null)
                        Text("فيديو MP4")
                    }
                }
            )

            FilterChip(
                selected = !isVideo,
                onClick = { if (!isDownloading) isVideo = false },
                modifier = Modifier.weight(1f).height(48.dp),
                label = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null)
                        Text("صوت MP3")
                    }
                }
            )
        }

        Button(
            onClick = { startDownload() },
            enabled = !isDownloading,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.Download, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isDownloading) "جارٍ التحميل..." else "تحميل",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (isDownloading || progress > 0f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Text(
                text = statusMessage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                textAlign = TextAlign.Center,
                fontSize = 14.sp
            )
        }
    }
}
