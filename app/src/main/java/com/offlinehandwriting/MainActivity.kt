package com.offlinehandwriting

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OfflineHandwritingApp(applicationContext) }
    }
}

data class Stroke(val points: List<Offset>)

@Composable
fun OfflineHandwritingApp(context: Context) {
    val scope = rememberCoroutineScope()
    val runner = remember { LocalModelRunner(context) }

    var strokes by remember { mutableStateOf(listOf<Stroke>()) }
    var current by remember { mutableStateOf(listOf<Offset>()) }
    var result by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("Auto") }
    var busy by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            withContext(Dispatchers.IO) { runner.prepare() }
            ready = true
        } catch (t: Throwable) {
            error = t.message ?: "שגיאה בטעינת מנוע הזיהוי"
        }
    }

    MaterialTheme {
        Scaffold { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(18.dp)
            ) {
                Text(
                    "כתב־יד אופליין",
                    fontSize = 28.sp,
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    when {
                        error != null -> "שגיאה במנוע המקומי"
                        busy -> "מזהה כתב־יד על המכשיר…"
                        ready -> "מנוע מקומי מוכן • ללא אינטרנט"
                        else -> "טוען מנוע מקומי…"
                    },
                    color = if (error == null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Auto", "עברית", "English").forEach { item ->
                        FilterChip(
                            selected = language == item,
                            onClick = { language = item },
                            label = { Text(item) }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Surface(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = 2.dp
                ) {
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .background(Color.White)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { p -> current = listOf(p) },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        current = current + change.position
                                    },
                                    onDragEnd = {
                                        if (current.isNotEmpty()) {
                                            strokes = strokes + Stroke(current)
                                        }
                                        current = emptyList()
                                    },
                                    onDragCancel = { current = emptyList() }
                                )
                            }
                    ) {
                        fun drawStroke(s: Stroke) {
                            if (s.points.size < 2) return
                            val path = Path().apply {
                                moveTo(s.points.first().x, s.points.first().y)
                                s.points.drop(1).forEach { lineTo(it.x, it.y) }
                            }
                            drawPath(
                                path,
                                Color.Black,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f)
                            )
                        }

                        strokes.forEach(::drawStroke)
                        drawStroke(Stroke(current))
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !busy && ready && strokes.isNotEmpty(),
                        onClick = {
                            busy = true
                            error = null
                            scope.launch {
                                try {
                                    val bitmap = withContext(Dispatchers.Default) {
                                        StrokeRenderer.render(strokes)
                                    }
                                    result = withContext(Dispatchers.Default) {
                                        runner.recognize(bitmap).clean(language)
                                    }
                                } catch (t: Throwable) {
                                    error = t.message ?: "שגיאה בזיהוי"
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (busy) "מזהה…" else "זיהוי")
                    }

                    OutlinedButton(
                        onClick = {
                            strokes = emptyList()
                            current = emptyList()
                            result = ""
                            error = null
                        }
                    ) {
                        Text("נקה")
                    }
                }

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = result,
                    onValueChange = { result = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    label = { Text("טקסט מזוהה") },
                    supportingText = { Text("הזיהוי, פיסוק והרווחים מעובדים מקומית במכשיר") }
                )
            }
        }
    }
}

private fun String.clean(language: String): String {
    var text = replace("\u0000", "")
        .replace(Regex("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    if (language == "עברית") {
        text = text.replace('־', '-').replace('״', '"').replace('׳', '\'')
    }
    return text
}

object StrokeRenderer {
    fun render(strokes: List<Stroke>): Bitmap {
        require(strokes.isNotEmpty())

        val points = strokes.flatMap { it.points }
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val margin = 28f

        val width = (maxX - minX + margin * 2).coerceAtLeast(32f).toInt()
        val height = (maxY - minY + margin * 2).coerceAtLeast(32f).toInt()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        canvas.drawColor(AndroidColor.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 6f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        for (stroke in strokes) {
            if (stroke.points.isEmpty()) continue
            val path = AndroidPath()
            path.moveTo(stroke.points.first().x - minX + margin, stroke.points.first().y - minY + margin)
            stroke.points.drop(1).forEach {
                path.lineTo(it.x - minX + margin, it.y - minY + margin)
            }
            canvas.drawPath(path, paint)
        }
        return bitmap
    }
}

class LocalModelRunner(private val context: Context) {
    companion object {
        private const val MODEL_ASSET = "mishkefet.pte"
        private const val CHARSET_ASSET = "charset.json"
        private const val MODEL_WIDTH = 2048
        private const val MODEL_HEIGHT = 64
    }

    @Volatile
    private var model: org.pytorch.executorch.Module? = null
    @Volatile
    private var charset: List<String> = emptyList()

    @Synchronized
    fun prepare() {
        if (model != null && charset.isNotEmpty()) return

        val modelPath = copyAssetToFiles(MODEL_ASSET)
        charset = loadCharset()
        model = org.pytorch.executorch.Module.load(modelPath)
    }

    fun recognize(bitmap: Bitmap): String {
        val currentModel = model ?: error("מנוע הזיהוי עדיין לא נטען")
        val input = bitmapToTensor(bitmap)
        val output = currentModel.forward(org.pytorch.executorch.EValue.from(input))[0].toTensor()
        val values = output.getDataAsFloatArray()
        val shape = output.shape()
        require(shape.size == 3) { "פלט מודל לא צפוי: " + shape.contentToString() }

        val time = shape[1].toInt()
        val classes = shape[2].toInt()
        require(classes == charset.size) {
            "Charset/model mismatch: model=" + classes + " charset=" + charset.size
        }

        val out = StringBuilder()
        var previous = 0

        for (t in 0 until time) {
            var bestIndex = 0
            var bestValue = Float.NEGATIVE_INFINITY
            val base = t * classes

            for (c in 0 until classes) {
                val v = values[base + c]
                if (v > bestValue) {
                    bestValue = v
                    bestIndex = c
                }
            }

            if (bestIndex != 0 && bestIndex != previous && bestIndex < charset.size) {
                out.append(charset[bestIndex])
            }
            previous = bestIndex
        }
        return out.toString()
    }

    private fun bitmapToTensor(bitmap: Bitmap): org.pytorch.executorch.Tensor {
        val scale = MODEL_HEIGHT.toFloat() / bitmap.height.coerceAtLeast(1)
        val resizedWidth = (bitmap.width * scale).toInt().coerceIn(16, MODEL_WIDTH)

        val resized = Bitmap.createScaledBitmap(bitmap, resizedWidth, MODEL_HEIGHT, true)

        val padded = Bitmap.createBitmap(
            MODEL_WIDTH,
            MODEL_HEIGHT,
            Bitmap.Config.ARGB_8888
        )
        val canvas = AndroidCanvas(padded)
        canvas.drawColor(AndroidColor.WHITE)
        canvas.drawBitmap(resized, 0f, 0f, null)

        val pixels = IntArray(MODEL_WIDTH * MODEL_HEIGHT)
        padded.getPixels(pixels, 0, MODEL_WIDTH, 0, 0, MODEL_WIDTH, MODEL_HEIGHT)

        val input = FloatArray(MODEL_WIDTH * MODEL_HEIGHT)
        for (i in pixels.indices) {
            val r = (pixels[i] shr 16) and 0xFF
            val g = (pixels[i] shr 8) and 0xFF
            val b = pixels[i] and 0xFF
            val gray = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            input[i] = 1f - gray
        }

        return org.pytorch.executorch.Tensor.fromBlob(
            input,
            longArrayOf(1, 1, MODEL_HEIGHT.toLong(), MODEL_WIDTH.toLong())
        )
    }

    private fun copyAssetToFiles(name: String): String {
        val target = java.io.File(context.filesDir, name)
        if (!target.exists() || target.length() == 0L) {
            context.assets.open(name).use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                    }
                }
            }
        }
        return target.absolutePath
    }

    private fun loadCharset(): List<String> {
        val text = context.assets.open(CHARSET_ASSET)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        val chars = org.json.JSONObject(text).getJSONArray("chars")
        return List(chars.length()) { chars.getString(it) }
    }
}
