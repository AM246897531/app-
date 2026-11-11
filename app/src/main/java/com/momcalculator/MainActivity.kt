package com.momcalculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.math.MathContext

private val teal = Color(0xFF00BDB3)
private val dark = Color(0xFF12363A)
class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  setContent { Calculator() }
 }
}
@Composable
fun Calculator() {
 var display by remember { mutableStateOf("0") }
 var stored by remember { mutableStateOf<BigDecimal?>(null) }
 var operation by remember { mutableStateOf("") }
 var fresh by remember { mutableStateOf(false) }
 var expression by remember { mutableStateOf("") }
 fun formatted(n: BigDecimal): String = n.stripTrailingZeros().toPlainString().take(22)
 fun number(): BigDecimal = display.toBigDecimalOrNull() ?: BigDecimal.ZERO
 fun compute(): Boolean {
  val a = stored ?: return true
  val b = number()
  val value = when(operation) {
   "+" -> a.add(b)
   "−" -> a.subtract(b)
   "×" -> a.multiply(b)
   "÷" -> if (b.compareTo(BigDecimal.ZERO)==0) null else a.divide(b, MathContext.DECIMAL64)
   else -> b
  }
  if(value == null) { display="לא ניתן לחלק באפס"; stored=null; operation=""; fresh=true; return false }
  display=formatted(value)
  stored=null
  operation=""
  return true
 }
 fun press(key: String) {
  when(key) {
   "C" -> { display="0"; stored=null; operation=""; expression=""; fresh=false }
   "⌫" -> { display=if(display.length<=1 || fresh) "0" else display.dropLast(1); fresh=false }
   "%" -> { display=formatted(number().divide(BigDecimal(100))); fresh=false }
   "+","−","×","÷" -> {
    if(stored != null && !fresh && !compute()) return
    stored=number(); operation=key; expression="${display} $key"; fresh=true
   }
   "=" -> {
    if(stored!=null) { expression="${formatted(stored!!)} $operation $display ="; compute(); fresh=true }
   }
   "." -> {
    if(fresh || display.toBigDecimalOrNull()==null) {display="0.";fresh=false}
    else if(!display.contains(".")) display+="."
   }
   else -> if(key.length==1 && key[0].isDigit()) {
    if(fresh || display=="0" || display.toBigDecimalOrNull()==null) {display=key;fresh=false}
    else if(display.length<16) display+=key
   }
  }
 }
 val rows=listOf(listOf("C","⌫","%","÷"),listOf("7","8","9","×"),listOf("4","5","6","−"),listOf("1","2","3","+"),listOf("0",".","="))
 MaterialTheme(colorScheme=lightColorScheme(primary=teal,background=Color(0xFFF5FAFA))) {
  Column(Modifier.fillMaxSize().background(Color(0xFFF5FAFA)).padding(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
   Spacer(Modifier.height(20.dp))
   Text("♥  המחשבון של אמא",color=dark,fontSize=26.sp,fontWeight=FontWeight.Bold,modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center)
   Spacer(Modifier.weight(.35f))
   Surface(shape=RoundedCornerShape(28.dp),color=Color.White,shadowElevation=3.dp,modifier=Modifier.fillMaxWidth()) {
    Column(Modifier.padding(22.dp),horizontalAlignment=Alignment.End) {
     Text(expression,color=Color.Gray,fontSize=17.sp,maxLines=1)
     Spacer(Modifier.height(12.dp))
     Text(display,color=dark,fontSize=if(display.length>12) 32.sp else 46.sp,fontWeight=FontWeight.SemiBold,maxLines=1)
    }
   }
   Spacer(Modifier.weight(.15f))
   rows.forEach { row ->
    Row(Modifier.fillMaxWidth().weight(1f),horizontalArrangement=Arrangement.spacedBy(9.dp)) {
     row.forEach { key ->
      val isOp=key in listOf("+","−","×","÷","%")
      val bg=when(key) { "="->teal;"C"->Color(0xFFFFE5E7);else->if(isOp) Color(0xFFD2F5F2) else Color.White }
      Button(onClick={press(key)},modifier=Modifier.weight(if(key=="0") 2f else 1f).fillMaxHeight(),shape=RoundedCornerShape(20.dp),contentPadding=PaddingValues(0.dp),colors=ButtonDefaults.buttonColors(containerColor=bg,contentColor=dark),elevation=ButtonDefaults.buttonElevation(defaultElevation=2.dp)) {
       Text(key,fontSize=27.sp,fontWeight=FontWeight.Medium)
      }
     }
     if(row.size==3) Spacer(Modifier.weight(1f))
    }
   }
   Spacer(Modifier.height(8.dp))
  }
 }
}
