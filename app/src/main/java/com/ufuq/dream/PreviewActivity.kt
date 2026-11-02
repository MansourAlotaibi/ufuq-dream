package com.ufuq.dream
import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
class PreviewActivity:Activity(){
 private lateinit var display:AmbientDisplay
 override fun onCreate(state:Bundle?){super.onCreate(state);window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;display=AmbientDisplay(this);setContentView(display)}
 override fun onResume(){super.onResume();display.start()}
 override fun onPause(){display.stop();super.onPause()}
 override fun onKeyDown(code:Int,event:KeyEvent?):Boolean{if(code==KeyEvent.KEYCODE_BACK||code==KeyEvent.KEYCODE_DPAD_CENTER){finish();return true};return super.onKeyDown(code,event)}
}
