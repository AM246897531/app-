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
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

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
    val runner = remember { LocalOnnxRecognizer(context) }

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
            error = t.message ?: "שגיאה בטעינת המנוע המקומי"
        }
    }

    MaterialTheme {
        Scaffold { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(18.dp)
            ) {
                Text("כתב־יד אופליין", fontSize = 28.sp, style = MaterialTheme.typography.headlineMedium)
                Text(
                    when {
                        error != null -> "המנוע המקומי לא נטען"
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
                    Modifier.fillMaxWidth().weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = 2.dp
                ) {
                    Canvas(
                        Modifier.fillMaxSize().background(Color.White).pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { p -> current = listOf(p) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    current = current + change.position
                                },
                                onDragEnd = {
                                    if (current.isNotEmpty()) strokes = strokes + Stroke(current)
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
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = 6f,
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                                )
                            )
                        }
                        strokes.forEach(::drawStroke)
                        drawStroke(Stroke(current))
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = ready && !busy && strokes.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            busy = true
                            error = null
                            scope.launch {
                                try {
                                    val bitmap = withContext(Dispatchers.Default) {
                                        StrokeRenderer.render(strokes)
                                    }
                                    result = withContext(Dispatchers.Default) {
                                        runner.recognize(bitmap, language)
                                    }
                                } catch (t: Throwable) {
                                    error = t.message ?: "שגיאה בזיהוי"
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    ) {
                        Text(if (busy) "מזהה…" else "זיהוי")
                    }

                    OutlinedButton(onClick = {
                        strokes = emptyList()
                        current = emptyList()
                        result = ""
                        error = null
                    }) { Text("נקה") }
                }

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = result,
                    onValueChange = { result = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    label = { Text("טקסט מזוהה") }
                )
            }
        }
    }
}

object StrokeRenderer {
    fun render(strokes: List<Stroke>): Bitmap {
        require(strokes.isNotEmpty())
        val points = strokes.flatMap { it.points }
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val margin = 36f

        val width = (maxX - minX + margin * 2).coerceAtLeast(64f).toInt()
        val height = (maxY - minY + margin * 2).coerceAtLeast(64f).toInt()

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
            path.moveTo(
                stroke.points.first().x - minX + margin,
                stroke.points.first().y - minY + margin
            )
            stroke.points.drop(1).forEach {
                path.lineTo(it.x - minX + margin, it.y - minY + margin)
            }
            canvas.drawPath(path, paint)
        }
        return bitmap
    }
}

class LocalOnnxRecognizer(private val context: Context) {
    companion object {
        private const val MODEL_ASSET = "mishkefet.onnx"
        private const val CHARSET_ASSET = "charset.json"
        private const val H = 64
        private const val W = 2048
    }

    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private var charset: List<String> = emptyList()

    @Synchronized
    fun prepare() {
        if (session != null) return
        val modelPath = copyAsset(MODEL_ASSET)
        charset = loadCharset()
        require(charset.isNotEmpty() && charset.first().isEmpty()) {
            "Invalid CTC charset"
        }
        val options = OrtSession.SessionOptions()
        session = environment.createSession(modelPath, options)
    }

    fun recognize(bitmap: Bitmap, language: String): String {
        val current = session ?: error("מנוע הזיהוי עדיין לא נטען")
        val inputArray = bitmapToTensor(bitmap)

        OnnxTensor.createTensor(
            environment,
            inputArray,
            longArrayOf(1, 1, H.toLong(), W.toLong())
        ).use { tensor ->
            val inputName = current.inputNames.iterator().next()
            current.run(mapOf(inputName to tensor)).use { outputs ->
                val value = outputs[0].value
                val logits = when (value) {
                    is Array<*> -> value
                    else -> error("פלט מודל לא צפוי")
                }
                return decode(logits, language)
            }
        }
    }

    private fun decode(value: Any, language: String): String {
        @Suppress("UNCHECKED_CAST")
        val batch = value as Array<Array<FloatArray>>
        require(batch.isNotEmpty()) { "Empty model output" }
        val sequence = batch[0]

        val text = StringBuilder()
        var previous = 0

        for (frame in sequence) {
            var best = 0
            var bestValue = Float.NEGATIVE_INFINITY
            for (i in frame.indices) {
                if (frame[i] > bestValue) {
                    bestValue = frame[i]
                    best = i
                }
            }

            if (best != 0 && best != previous && best < charset.size) {
                text.append(charset[best])
            }
            previous = best
        }

        return clean(text.toString(), language)
    }

    private fun clean(input: String, language: String): String {
        var text = input
            .replace(' '.toString(), "")
            .replace(Regex("[\u200E\u200F\u202A-\u202E\u2066-\u2069]"), "")
            .replace(Regex("\s+"), " ")
            .trim()

        if (language == "עברית") {
            text = text.replace('־', '-').replace('״', '"').replace('׳', ''')
        }
        return text
    }

    private fun bitmapToTensor(bitmap: Bitmap): FloatBuffer {
        val scale = H.toFloat() / bitmap.height.coerceAtLeast(1)
        val resizedWidth = (bitmap.width * scale).toInt().coerceIn(16, W)

        val resized = Bitmap.createScaledBitmap(bitmap, resizedWidth, H, true)
        val pixels = IntArray(W * H)
        val padded = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(padded)
        canvas.drawColor(AndroidColor.WHITE)
        canvas.drawBitmap(resized, 0f, 0f, null)
        padded.getPixels(pixels, 0, W, 0, 0, W, H)

        val buffer = ByteBuffer
            .allocateDirect(W * H * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val gray = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            buffer.put(1f - gray)
        }
        buffer.rewind()
        padded.recycle()
        resized.recycle()
        return buffer
    }

    private fun copyAsset(name: String): String {
        val target = java.io.File(context.filesDir, name)
        if (!target.exists() || target.length() == 0L) {
            context.assets.open(name).use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output, 1024 * 1024)
                }
            }
        }
        return target.absolutePath
    }

    private fun loadCharset(): List<String> {
        val text = context.assets.open(CHARSET_ASSET)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        val json = org.json.JSONObject(text)
        val chars = json.getJSONArray("chars")
        return List(chars.length()) { chars.getString(it) }
    }
}
