package com.ufuq.dream
import android.content.*
import android.os.Build
import android.service.dreams.DreamService
class UfuqDreamService:DreamService(){
 private lateinit var display:AmbientDisplay
 private var registered=false
 private var sessionRunning=false
 private val receiver=object:BroadcastReceiver(){override fun onReceive(context:Context?,intent:Intent?){display.reschedule()}}
 override fun onAttachedToWindow(){super.onAttachedToWindow();isInteractive=false;isFullscreen=true;isScreenBright=false;display=AmbientDisplay(this);setContentView(display)}
 override fun onDreamingStarted(){super.onDreamingStarted();if(sessionRunning)return
 sessionRunning=true
 val repo=SettingsRepository(this)
 val settings=repo.load()
 if(settings.sceneChoice<0 && settings.imageMode==0 && settings.rotationMinutes>0){display.stop();display=AmbientDisplay(this,startScene=repo.nextAutoStart());setContentView(display)}
 display.start(fadeIn=true)
 if(!registered){val filter=IntentFilter().apply{addAction(Intent.ACTION_TIME_CHANGED);addAction(Intent.ACTION_TIMEZONE_CHANGED);addAction(Intent.ACTION_DATE_CHANGED)}
 if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED) else registerReceiver(receiver,filter);registered=true}}
 private fun stop(){sessionRunning=false;if(::display.isInitialized)display.stop();if(registered){unregisterReceiver(receiver);registered=false}}
 override fun onDreamingStopped(){stop();super.onDreamingStopped()}
 override fun onDetachedFromWindow(){stop();super.onDetachedFromWindow()}
 override fun onDestroy(){stop();super.onDestroy()}
}
