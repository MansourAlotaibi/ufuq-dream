package com.ufuq.dream
import android.graphics.Color

data class AmbientTheme(val name:String,val base:Int,val middle:Int,val glow:Int,val ink:Int,val muted:Int,val accent:Int)
object ThemeManager {
 private fun c(s:String)=Color.parseColor(s)
 val themes=listOf(
 AmbientTheme("الأخضر السعودي · Saudi Green",c("#061A15"),c("#123E31"),c("#255D47"),c("#EEEADD"),c("#A9BDB0"),c("#C6AE78")),
 AmbientTheme("أرض الصحراء · Desert Earth",c("#211914"),c("#493329"),c("#765442"),c("#F0E3CD"),c("#C5AF98"),c("#D0AE76")),
 AmbientTheme("ليل الرياض · Riyadh Night",c("#101824"),c("#1D2C3D"),c("#2A3E52"),c("#EBEEF2"),c("#B8C4D2"),c("#B8CBDD")))
 fun get(index:Int)=themes[index.coerceIn(0,themes.lastIndex)]
}
