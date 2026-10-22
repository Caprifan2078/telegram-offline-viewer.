package org.openarchive.telegram;
import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Rational;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.VideoView;
public class RoundPlayerActivity extends Activity {
 private VideoView video;
 private View controls;
 private boolean playing;
 @Override public void onCreate(Bundle state) {
  super.onCreate(state);
  FrameLayout frame=new FrameLayout(this);
  setContentView(frame);
  video=new VideoView(this);
  frame.addView(video,new FrameLayout.LayoutParams(-1,-1));
  Button pip=new Button(this);
  pip.setText("Плавающее окно");
  FrameLayout.LayoutParams bar=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
  frame.addView(pip,bar); controls=pip;
  pip.setOnClickListener(v->enterPip());
  Uri uri=getIntent().getData();
  if(uri==null){finish();return;}
  video.setOnPreparedListener(mp->{playing=true;mp.setLooping(true);video.start();});
  video.setOnErrorListener((mp,w,e)->{pip.setText("Не удалось открыть MP4");return true;});
  video.setVideoURI(uri);
 }
 private void enterPip(){
  if(!playing || Build.VERSION.SDK_INT<26)return;
  try{enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(new Rational(1,1)).build());}catch(Exception ignored){}
 }
 @Override protected void onUserLeaveHint(){super.onUserLeaveHint();enterPip();}
 @Override public void onPictureInPictureModeChanged(boolean mode,Configuration config){super.onPictureInPictureModeChanged(mode,config);controls.setVisibility(mode?View.GONE:View.VISIBLE);}
 @Override protected void onDestroy(){if(video!=null)video.stopPlayback();super.onDestroy();}
}
