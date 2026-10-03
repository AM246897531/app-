package com.offlinehandwriting

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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OfflineHandwritingApp() }
    }
}

data class Stroke(val points: List<Offset>)

@Composable
fun OfflineHandwritingApp() {
    var strokes by remember { mutableStateOf(listOf<Stroke>()) }
    var current by remember { mutableStateOf(listOf<Offset>()) }
    var result by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("Auto") }

    MaterialTheme {
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Text("כתב־יד אופליין", fontSize = 28.sp, style = MaterialTheme.typography.headlineMedium)
            Text("זיהוי מקומי בלבד • ללא אינטרנט", color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Auto","עברית","English").forEach { item ->
                    FilterChip(selected = language == item, onClick = { language = item }, label = { Text(item) })
                }
            }
            Spacer(Modifier.height(12.dp))
            Surface(Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(24.dp), tonalElevation = 2.dp) {
                Canvas(
                    Modifier.fillMaxSize().background(Color.White).pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { p -> current = listOf(p) },
                            onDrag = { change, _ -> change.consume(); current = current + change.position },
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
                        drawPath(path, Color.Black, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f))
                    }
                    strokes.forEach(::drawStroke)
                    drawStroke(Stroke(current))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    result = LocalRecognizer.recognize(strokes, language)
                }, modifier = Modifier.weight(1f)) { Text("זיהוי") }
                OutlinedButton(onClick = { strokes = emptyList(); current = emptyList(); result = "" }) { Text("נקה") }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = result, onValueChange = { result = it },
                modifier = Modifier.fillMaxWidth(), minLines = 2,
                label = { Text("טקסט מזוהה") }
            )
        }
    }
}

object LocalRecognizer {
    fun recognize(strokes: List<Stroke>, language: String): String {
        if (strokes.isEmpty()) return ""
        return when (language) {
            "עברית" -> "מנוע הזיהוי המקומי נטען — עברית"
            "English" -> "Local handwriting engine loading — English"
            else -> "מנוע הזיהוי המקומי נטען — עברית / English"
        }
    }
}
