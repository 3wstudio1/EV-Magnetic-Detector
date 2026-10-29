package com.example.evmagneticdetector;

import android.app.Activity;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.SeekBar;
import java.util.ArrayDeque;

public class MainActivity extends Activity implements SensorEventListener {
  SensorManager manager; Sensor sensor; Vibrator vibrator;
  TextView reading, status, sensitivity;
  Plot plot; double baseline=0, threshold=4, lastAlert=0;
  long lastVibration=0, lastUpdate=0; boolean initialized=false;
  public void onCreate(Bundle b) {
    super.onCreate(b);
    final Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
      try { getSharedPreferences("diagnostics",MODE_PRIVATE).edit().putString("lastCrash",android.util.Log.getStackTraceString(error)).commit(); } catch(Exception ignored){}
      if(previous!=null)previous.uncaughtException(thread,error);
    });
    manager=(SensorManager)getSystemService(SENSOR_SERVICE);
    sensor=manager==null?null:manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    vibrator=(Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
    LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(24,28,24,20);
    root.setBackgroundColor(Color.rgb(12,20,31));
    TextView title=new TextView(this); title.setText("EV MAGNETIC DETECTOR"); title.setTextSize(22); title.setTextColor(Color.WHITE); root.addView(title);
    reading=new TextView(this); reading.setText("Waiting for sensor..."); reading.setTextSize(22); reading.setTextColor(Color.CYAN); root.addView(reading);
    status=new TextView(this); status.setText("Experimental magnetic anomaly monitor"); status.setTextColor(Color.LTGRAY); status.setTextSize(16); root.addView(status);
    sensitivity=new TextView(this); sensitivity.setText("Alert deviation: 4.0 µT"); sensitivity.setTextColor(Color.WHITE); root.addView(sensitivity);
    SeekBar slider=new SeekBar(this); slider.setMax(195); slider.setProgress(30); root.addView(slider);
    slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
      public void onProgressChanged(SeekBar s,int p,boolean u){threshold=1+p/10.0; sensitivity.setText(String.format(java.util.Locale.US,"Alert deviation: %.1f µT",threshold));}
      public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}
    });
    plot=new Plot(); root.addView(plot,new LinearLayout.LayoutParams(-1,0,1));
    TextView note=new TextView(this); note.setText("Keep phone stationary. Magnetic anomalies do NOT identify vehicles or guarantee 10 m detection. Do not rely on this for road safety."); note.setTextColor(Color.LTGRAY); root.addView(note);
    setContentView(root);
    if(sensor==null){status.setText("No magnetometer found on this device");}
    String crash=getSharedPreferences("diagnostics",MODE_PRIVATE).getString("lastCrash",null);
    if(crash!=null){android.widget.Button details=new android.widget.Button(this);details.setText("Show previous crash details");root.addView(details);details.setOnClickListener(v->new android.app.AlertDialog.Builder(this).setTitle("Crash details").setMessage(crash).setPositiveButton("OK",null).show());}
  }
  protected void onResume(){super.onResume();if(sensor!=null && manager!=null)manager.registerListener(this,sensor,SensorManager.SENSOR_DELAY_UI);}
  protected void onPause(){super.onPause();if(manager!=null)manager.unregisterListener(this);}
  public void onAccuracyChanged(Sensor s,int a){}
  public void onSensorChanged(SensorEvent e){
    long tick=android.os.SystemClock.elapsedRealtime();
    if(tick-lastUpdate<80)return;
    lastUpdate=tick;
    if(e.values==null||e.values.length<3||reading==null||status==null||plot==null)return;
    double x=e.values[0],y=e.values[1],z=e.values[2],m=Math.sqrt(x*x+y*y+z*z);
    if(!initialized){baseline=m;initialized=true;}
    double deviation=Math.abs(m-baseline);
    boolean alert=deviation>=threshold;
    if(!alert)baseline=baseline*.995+m*.005;
    reading.setText(String.format(java.util.Locale.US,"Field %.2f µT\nChange %.2f µT",m,deviation));
    status.setText(alert?"MAGNETIC CHANGE DETECTED":"Monitoring magnetic changes");
    status.setTextColor(alert?Color.YELLOW:Color.LTGRAY);
    plot.add((float)deviation,(float)threshold);
    long now=android.os.SystemClock.elapsedRealtime();
    if(alert && now-lastVibration>1800){lastVibration=now;try{if(vibrator!=null&&vibrator.hasVibrator())vibrator.vibrate(VibrationEffect.createOneShot(220,VibrationEffect.DEFAULT_AMPLITUDE));}catch(RuntimeException ignored){}}
  }
  class Plot extends View{
    ArrayDeque<Float> points=new ArrayDeque<>();Paint paint=new Paint(3);float limit=4;
    Plot(){super(MainActivity.this);}
    void add(float v,float t){limit=t;points.addLast(v);if(points.size()>150)points.removeFirst();invalidate();}
    protected void onDraw(Canvas c){super.onDraw(c);int w=getWidth(),h=getHeight();c.drawColor(Color.rgb(17,31,45));float scale=Math.max(10,limit*2);
      paint.setColor(Color.DKGRAY);paint.setStrokeWidth(2);c.drawLine(0,h/2f,w,h/2f,paint);
      paint.setColor(Color.YELLOW);float ty=h-h*limit/scale;c.drawLine(0,ty,w,ty,paint);
      paint.setColor(Color.CYAN);paint.setStrokeWidth(3);float prevX=0,prevY=h;int i=0;
      for(float v:points){float xx=i*w/150f,yy=h-Math.min(v,scale)*h/scale;if(i>0)c.drawLine(prevX,prevY,xx,yy,paint);prevX=xx;prevY=yy;i++;}
    }
  }
}
