package com.luke.summer;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.*;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
 private static final String ORIGIN="https://appassets.androidplatform.net";
 private WebView web;
 private SoftStartup startup;
 private String launchSeason="spring",launchMode="day",launchStyle="",shownSplashStyle="";
 private android.view.ContextThemeWrapper launchContext;
 private boolean feedbackAllowed=true;
 private long lastFeedback=-1000L;
 private android.content.SharedPreferences uiPreferences(){return getSharedPreferences("luke-ui-preferences-v1",MODE_PRIVATE);}
 private static String seasonAtMonth(int month){return new String[]{"spring","summer","autumn","winter"}[((month+10)%12)/3];}
 private static boolean darkAtPeriod(String period,double hour){
  if("清晨".equals(period))hour=6.5;else if("早上".equals(period))hour=9;
  else if("正午".equals(period))hour=12;else if("午后".equals(period))hour=15;
  else if("傍晚".equals(period))hour=18;else if("夜晚".equals(period))hour=20;else if("深夜".equals(period))hour=23;
  return hour<5.75||hour>19;
 }
 private int launchResource(String name,String type){return getResources().getIdentifier(name,type,getPackageName());}
 private void configureLaunch(){
  android.content.SharedPreferences prefs=uiPreferences();Calendar clock=Calendar.getInstance();
  String selected=prefs.getString("season","auto"),period=prefs.getString("period","auto");
  launchSeason=Arrays.asList("spring","summer","autumn","winter").contains(selected)?selected:seasonAtMonth(clock.get(Calendar.MONTH));
  double hour=clock.get(Calendar.HOUR_OF_DAY)+clock.get(Calendar.MINUTE)/60.0+clock.get(Calendar.SECOND)/3600.0;
  launchMode=darkAtPeriod(period,hour)?"night":"day";
  launchStyle="Luke"+launchSeason.substring(0,1).toUpperCase(Locale.ROOT)+launchSeason.substring(1)+(launchMode.equals("night")?"Night":"Day");
  int theme=launchResource(launchStyle,"style");
  if(theme!=0){launchContext=new android.view.ContextThemeWrapper(this,theme);if(web==null)setTheme(theme);}
  else launchContext=new android.view.ContextThemeWrapper(this,getApplicationInfo().theme);
  if(android.os.Build.VERSION.SDK_INT>=31&&theme!=0){
   try{getSplashScreen().setSplashScreenTheme(theme);prefs.edit().putString("cachedSplashStyle",launchStyle).apply();}catch(RuntimeException ignored){}
  }
 }
 private int launchColor(String name){
  int id=launchResource(name+"_"+launchSeason+"_"+launchMode,"color");
  return getColor(id!=0?id:launchResource(name,"color"));
 }
 private android.graphics.drawable.Drawable launchMark(boolean animated){
  int id=launchResource("luke_launch_"+launchSeason+(animated?"_animated":"_mark"),"drawable");
  return launchContext.getDrawable(id);
 }
 private void touchFeedback(String kind){
  if(!Arrays.asList("press","selection","confirm").contains(kind))return;
  runOnUiThread(()->{
   if(isDestroyed()||!contentReady||!feedbackAllowed||!web.isShown()||!web.hasWindowFocus())return;
   long now=android.os.SystemClock.elapsedRealtime();if(now-lastFeedback<90L)return;lastFeedback=now;
   int effect=android.view.HapticFeedbackConstants.VIRTUAL_KEY;
   if("selection".equals(kind))effect=android.view.HapticFeedbackConstants.CLOCK_TICK;
   else if("confirm".equals(kind)&&android.os.Build.VERSION.SDK_INT>=30)effect=android.view.HapticFeedbackConstants.CONFIRM;
   boolean applied=web.performHapticFeedback(effect);
   android.util.Log.d("LukeFeedback","kind="+kind+" accepted="+applied);
  });
 }
 private android.widget.FrameLayout viewport;
 private boolean darkSystemBars=true;
 private boolean keyboardVisible=false;
 private String systemColor="";
 private boolean contentReady=false,readyPosted=false;
 private final ExecutorService workers=Executors.newFixedThreadPool(3);
 private final ConcurrentHashMap<String,HttpURLConnection> requests=new ConcurrentHashMap<>();
 private final Set<String> active=ConcurrentHashMap.newKeySet();
 private ValueCallback<Uri[]> fileCallback;
 private GeolocationPermissions.Callback locationCallback;
 private String locationOrigin,exportText;

 @Override public void onCreate(Bundle state){
  shownSplashStyle=uiPreferences().getString("cachedSplashStyle","");
  feedbackAllowed=uiPreferences().getBoolean("feedbackEnabled",true);
  configureLaunch();
  super.onCreate(state);
  viewport=new android.widget.FrameLayout(this);web=new WebView(this);web.setVisibility(android.view.View.INVISIBLE);viewport.addView(web,new android.widget.FrameLayout.LayoutParams(-1,-1));setContentView(viewport);startup=new SoftStartup();startup.install();showSystemBars();
  if(android.os.Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);viewport.setOnApplyWindowInsetsListener((v,insets)->{setKeyboardVisible(insets.isVisible(android.view.WindowInsets.Type.ime()));android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout()|android.view.WindowInsets.Type.ime());android.widget.FrameLayout.LayoutParams lp=(android.widget.FrameLayout.LayoutParams)web.getLayoutParams();if(lp.leftMargin!=bars.left||lp.topMargin!=bars.top||lp.rightMargin!=bars.right||lp.bottomMargin!=bars.bottom){lp.setMargins(bars.left,bars.top,bars.right,bars.bottom);web.setLayoutParams(lp);}return android.view.WindowInsets.CONSUMED;});viewport.requestApplyInsets();}
  if(android.os.Build.VERSION.SDK_INT<30)viewport.getViewTreeObserver().addOnGlobalLayoutListener(()->{android.graphics.Rect frame=new android.graphics.Rect();viewport.getWindowVisibleDisplayFrame(frame);int height=viewport.getRootView().getHeight();setKeyboardVisible(height-frame.bottom>height*.2);});
  WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setOffscreenPreRaster(true);s.setAllowFileAccess(false);s.setAllowContentAccess(true);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setGeolocationEnabled(true);s.setSupportMultipleWindows(false);
  web.addJavascriptInterface(new Bridge(),"LukeAndroid");
  web.setWebViewClient(new WebViewClient(){
   @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest r){
    Uri u=r.getUrl();if(!ORIGIN.equals(u.getScheme()+"://"+u.getAuthority()))return denied();
    String path=u.getPath();if(path==null||path.contains("..")||path.contains("\\"))return denied();
    if(path.equals("/"))path="/index.html";
    try{String mime=path.endsWith(".js")?"application/javascript":path.endsWith(".css")?"text/css":path.endsWith(".html")?"text/html":path.endsWith(".jpg")?"image/jpeg":path.endsWith(".png")?"image/png":path.endsWith(".svg")?"image/svg+xml":path.endsWith(".webp")?"image/webp":"application/octet-stream";
     return new WebResourceResponse(mime,"UTF-8",getAssets().open("web"+path));
    }catch(IOException e){return denied();}
   }
   @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){
    if(request.isForMainFrame()&&!contentReady)startup.fail();
   }
   @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest r){
    Uri u=r.getUrl();if((ORIGIN+"/").equals(u.toString()))return false;
    if(r.isForMainFrame()&&"https".equals(u.getScheme()))try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception ignored){}
    return true;
   }
  });
  web.setWebChromeClient(new WebChromeClient(){
   @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams p){
    if(fileCallback!=null)fileCallback.onReceiveValue(null);fileCallback=callback;
    try{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,10);}catch(Exception e){fileCallback.onReceiveValue(null);fileCallback=null;toast("无法打开文件选择器");}return true;
   }
   @Override public void onGeolocationPermissionsShowPrompt(String origin,GeolocationPermissions.Callback callback){
    if(!origin.equals(ORIGIN)&&!origin.equals(ORIGIN+"/")){callback.invoke(origin,false,false);return;}
    if(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED){callback.invoke(origin,true,false);return;}
    locationCallback=callback;locationOrigin=origin;requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION},12);
   }
  });
  web.loadUrl(ORIGIN+"/");
 }
 private void publishKeyboard(){web.evaluateJavascript("document.documentElement.dataset.keyboard='"+keyboardVisible+"'",null);}
 private void setKeyboardVisible(boolean visible){if(keyboardVisible==visible)return;keyboardVisible=visible;publishKeyboard();}
 private void showSystemBars(){
  getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
  if(android.os.Build.VERSION.SDK_INT>=30){android.view.WindowInsetsController c=getWindow().getInsetsController();if(c!=null){c.show(android.view.WindowInsets.Type.systemBars());int mask=android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;c.setSystemBarsAppearance(darkSystemBars?0:mask,mask);}}
  else getWindow().getDecorView().setSystemUiVisibility(darkSystemBars?0:android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
 }
 @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused)showSystemBars();}
 private WebResourceResponse denied(){return new WebResourceResponse("text/plain","UTF-8",403,"Forbidden",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
 private void toast(String text){runOnUiThread(()->Toast.makeText(this,text,Toast.LENGTH_LONG).show());}
 @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==12&&locationCallback!=null){locationCallback.invoke(locationOrigin,checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED,false);locationCallback=null;}}
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
  if(request==10&&fileCallback!=null){fileCallback.onReceiveValue(result==RESULT_OK&&data!=null&&data.getData()!=null?new Uri[]{data.getData()}:null);fileCallback=null;}
  if(request==11){String text=exportText;exportText=null;if(result==RESULT_OK&&data!=null&&text!=null){Uri uri=data.getData();workers.execute(()->{try{try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException();out.write(text.getBytes(StandardCharsets.UTF_8));}runOnUiThread(()->{if(!isDestroyed())web.evaluateJavascript("localStorage.setItem('luke-backup-confirmed',Date.now().toString());window.dispatchEvent(new Event('luke-backup-saved'))",null);});toast("备份已保存");}catch(Exception e){toast("备份未保存，请重试");}});}}
 }
 @Override public boolean dispatchKeyEvent(android.view.KeyEvent event){if(event.getKeyCode()==android.view.KeyEvent.KEYCODE_BACK){if(event.getAction()==android.view.KeyEvent.ACTION_UP&&!event.isCanceled())onBackPressed();return true;}return super.dispatchKeyEvent(event);}
 @Override public void onBackPressed(){if(!contentReady){moveTaskToBack(true);return;}if(keyboardVisible){((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(web.getWindowToken(),0);web.evaluateJavascript("document.activeElement instanceof HTMLElement&&document.activeElement.blur()",null);return;}web.evaluateJavascript("!!(window.__lukeBack&&window.__lukeBack())",handled->{if(!"true".equals(handled))moveTaskToBack(true);});}
 @Override protected void onStop(){super.onStop();if(startup!=null)startup.finishHidden();}
 @Override protected void onDestroy(){if(startup!=null)startup.dispose();for(HttpURLConnection c:requests.values())c.disconnect();workers.shutdownNow();web.removeJavascriptInterface("LukeAndroid");web.destroy();super.onDestroy();}
 private void deliver(String id,int status,String body){if(!active.remove(id))return;String script="window.__lukeNetwork&&window.__lukeNetwork("+JSONObject.quote(id)+","+status+","+JSONObject.quote(body)+")";runOnUiThread(()->{if(!isDestroyed())web.evaluateJavascript(script,null);});}
 private void streamPart(String id,int status,String type,String text,boolean done){if(!active.contains(id))return;if(done)active.remove(id);String script="window.__lukeStreaming&&window.__lukeStreaming("+JSONObject.quote(id)+","+status+","+JSONObject.quote(type)+","+JSONObject.quote(text)+","+done+")";runOnUiThread(()->{if(!isDestroyed())web.evaluateJavascript(script,null);});}
 private void applySystemTheme(){String color=systemColor.isEmpty()?"#214f4c":systemColor;boolean dark=darkSystemBars;int value=Color.parseColor(color);viewport.setBackgroundColor(value);getWindow().setStatusBarColor(value);getWindow().setNavigationBarColor(value);if(android.os.Build.VERSION.SDK_INT>=30){android.view.WindowInsetsController c=getWindow().getInsetsController();if(c!=null){int mask=android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;c.setSystemBarsAppearance(dark?0:mask,mask);}}else getWindow().getDecorView().setSystemUiVisibility(dark?0:android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);}
 // Native startup only: no remote assets, new dependencies, or minimum display delay.
 private final class SoftStartup {
  private static final long EXIT_MS=380L, FAILURE_MS=8000L;
  private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
  private final Runnable watchdog=()->fail();
  private final android.view.animation.Interpolator ease=new android.view.animation.PathInterpolator(.4f,0f,.2f,1f);
  private android.widget.FrameLayout layer;
  private android.widget.ImageView mark;
  private android.widget.LinearLayout failure;
  private android.animation.ValueAnimator bars;
  private android.view.ViewTreeObserver.OnPreDrawListener hold;
  private Runnable removePlatform;
  private android.view.View platformView;
  private android.view.ViewTreeObserver.OnDrawListener warmDraw;
  private android.view.Choreographer.FrameCallback exitFrame;
  private Runnable finishExit;
  private long markStarted;
  private boolean themeTransition;
  private boolean released,disposed,finishing;
  private int background,generation;

  private int resource(String name,String type){
   int id=getResources().getIdentifier(name,type,getPackageName());
   if(id==0)throw new IllegalStateException("Missing startup resource: "+name);
   return id;
  }
  private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
  private boolean motion(){return android.animation.ValueAnimator.areAnimatorsEnabled();}
  void install(){
   background=launchColor("luke_launch_background");
   android.util.Log.i("LukeSeason","launch season="+launchSeason+" mode="+launchMode);
   darkSystemBars="night".equals(launchMode);
   viewport.setBackgroundColor(background);web.setBackgroundColor(background);
   getWindow().setStatusBarColor(background);getWindow().setNavigationBarColor(background);
   layer=new android.widget.FrameLayout(MainActivity.this);layer.setBackgroundColor(background);
   layer.setClickable(true);layer.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES);
   layer.setContentDescription("四时与你，正在打开");
   mark=new android.widget.ImageView(MainActivity.this);
   mark.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
   mark.setImageDrawable(launchMark(false));
   layer.addView(mark,new android.widget.FrameLayout.LayoutParams(dp(288),dp(288),android.view.Gravity.CENTER));
   viewport.addView(layer,new android.widget.FrameLayout.LayoutParams(-1,-1));
   if(android.os.Build.VERSION.SDK_INT>=31){
    // Draw the WebView's prepared frame behind the one system splash, not a second splash.
    hold=()->released;
    viewport.getViewTreeObserver().addOnPreDrawListener(hold);
    PlatformSplash.install(MainActivity.this,this);
   }else{released=true;mark.postOnAnimation(()->{if(!disposed&&!contentReady)startMark();});}
   handler.postDelayed(watchdog,FAILURE_MS);
  }
  private void startMark(){
   markStarted=android.os.SystemClock.uptimeMillis();
   if(!motion()){mark.setImageDrawable(launchMark(false));return;}
   mark.setImageDrawable(launchMark(true));
   android.graphics.drawable.Drawable d=mark.getDrawable();
   if(d instanceof android.graphics.drawable.Animatable)((android.graphics.drawable.Animatable)d).start();
  }
  private void stopMark(){
   android.graphics.drawable.Drawable d=mark.getDrawable();
   if(d instanceof android.graphics.drawable.Animatable)((android.graphics.drawable.Animatable)d).stop();
  }
  private void releaseDraw(){
   released=true;
   if(hold!=null){if(viewport.getViewTreeObserver().isAlive())viewport.getViewTreeObserver().removeOnPreDrawListener(hold);hold=null;}
   viewport.invalidate();
  }
  void systemExit(android.view.View splash,android.view.View icon,Runnable remove,long introRemaining){
   if(disposed){remove.run();return;}
   removePlatform=remove;platformView=splash;
   icon=seasonalSystemIcon(splash,icon);
   if(!motion()){finishPlatform();return;}
   // Let the ongoing vector settle; never reset it to its final frame at pageReady.
   long delay=Math.min(180L,Math.max(0L,introRemaining-EXIT_MS));
   animateExit(splash,icon,delay,this::finishPlatform,"system");
  }
  private android.widget.ImageView seasonalOverlay;
  private android.view.View seasonalOriginal;
  private int seasonalFrom;
  private android.view.View seasonalSystemIcon(android.view.View splash,android.view.View icon){
   if(launchStyle.equals(shownSplashStyle)||!(splash instanceof android.widget.FrameLayout)||icon==null)return icon;
   android.graphics.drawable.Drawable bg=splash.getBackground();
   seasonalFrom=bg instanceof android.graphics.drawable.ColorDrawable?((android.graphics.drawable.ColorDrawable)bg).getColor():background;
   seasonalOriginal=icon;seasonalOverlay=new android.widget.ImageView(MainActivity.this);
   seasonalOverlay.setImageDrawable(launchMark(false));seasonalOverlay.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
   int width=icon.getWidth(),height=icon.getHeight();if(width<1||height<1){width=dp(288);height=dp(288);}
   android.widget.FrameLayout.LayoutParams lp=new android.widget.FrameLayout.LayoutParams(width,height);
   lp.leftMargin=icon.getLeft();lp.topMargin=icon.getTop();
   seasonalOverlay.setAlpha(0f);((android.widget.FrameLayout)splash).addView(seasonalOverlay,lp);
   return seasonalOverlay;
  }
  private void blendSeasonalSystem(float progress){
   if(seasonalOverlay==null||platformView==null)return;
   float blend=ease.getInterpolation(Math.min(1f,progress));
   seasonalOverlay.setAlpha(blend);if(seasonalOriginal!=null)seasonalOriginal.setAlpha(1f-blend);
   int target=launchColor("luke_launch_background");
   int c=Color.rgb(Math.round(Color.red(seasonalFrom)+(Color.red(target)-Color.red(seasonalFrom))*blend),Math.round(Color.green(seasonalFrom)+(Color.green(target)-Color.green(seasonalFrom))*blend),Math.round(Color.blue(seasonalFrom)+(Color.blue(target)-Color.blue(seasonalFrom))*blend));
   platformView.setBackgroundColor(c);
  }
  void appearanceUpdated(){
   if(disposed||contentReady||finishing)return;
   int color=launchColor("luke_launch_background");background=color;layer.setBackgroundColor(color);viewport.setBackgroundColor(color);web.setBackgroundColor(color);
   android.graphics.drawable.Drawable before=mark.getDrawable();
   android.graphics.drawable.TransitionDrawable blend=new android.graphics.drawable.TransitionDrawable(new android.graphics.drawable.Drawable[]{before,launchMark(false)});
   mark.setImageDrawable(blend);blend.setCrossFadeEnabled(true);blend.startTransition(motion()?160:0);
   android.util.Log.i("LukeSeason","resolved season="+launchSeason+" mode="+launchMode);
  }
  private void finishPlatform(){
   seasonalOverlay=null;seasonalOriginal=null;
   if(platformView!=null){platformView.animate().cancel();platformView=null;}
   if(removePlatform!=null){Runnable remove=removePlatform;removePlatform=null;remove.run();}
   if(!disposed&&contentReady){web.getSettings().setOffscreenPreRaster(false);web.evaluateJavascript("delete document.documentElement.dataset.nativeLaunching",null);}
  }
  private void animateExit(android.view.View surface,android.view.View icon,long delay,Runnable remove,String name){
   final int token=generation;
   final boolean[] started={false};
   Runnable begin=()->{
    if(started[0]||disposed||token!=generation)return;started[0]=true;
    clearWarmDraw();
    if(!motion()){remove.run();return;}
    // Rasterise the static cover before starting the clock. WebView's first real draw
    // can otherwise consume the whole wall-clock animator and expose only its last frame.
    final int oldLayer=surface.getLayerType();
    surface.setLayerType(android.view.View.LAYER_TYPE_HARDWARE,null);surface.buildLayer();
    final float fromAlpha=surface.getAlpha(),fromX=icon==null?1f:icon.getScaleX(),fromY=icon==null?1f:icon.getScaleY();
    final long began=android.os.SystemClock.uptimeMillis();
    final long[] previous={0L},maxGap={0L};final float[] elapsed={0f};final int[] frames={0};
    final boolean[] ended={false};
    finishExit=()->{
     if(ended[0])return;ended[0]=true;
     if(exitFrame!=null){android.view.Choreographer.getInstance().removeFrameCallback(exitFrame);exitFrame=null;}
     surface.setAlpha(0f);surface.setLayerType(oldLayer,null);finishExit=null;
     android.util.Log.i("LukeMotion",name+" exit-complete ms="+(android.os.SystemClock.uptimeMillis()-began)+" frames="+frames[0]+" maxGapMs="+maxGap[0]);
     remove.run();
    };
    android.util.Log.i("LukeMotion",name+" exit-start");
    exitFrame=now->{
     if(disposed||token!=generation)return;
     if(!motion()||viewport.getWindowVisibility()!=android.view.View.VISIBLE){if(finishExit!=null)finishExit.run();return;}
     if(previous[0]!=0){
      long gap=Math.max(0L,(now-previous[0])/1000000L);maxGap[0]=Math.max(maxGap[0],gap);
      // Do not spend an entire animation during a blocked first frame. Normal 60/90/120Hz
      // timing is unchanged; after a late frame continue through the missing visual states.
      elapsed[0]+=Math.min(32L,gap);
     }
     previous[0]=now;frames[0]++;
     float progress=ease.getInterpolation(Math.min(1f,elapsed[0]/EXIT_MS));
     if(surface==platformView)blendSeasonalSystem(elapsed[0]/180f);
     surface.setAlpha(fromAlpha*(1f-progress));
     if(icon!=null){icon.setScaleX(fromX*(1f-.02f*progress));icon.setScaleY(fromY*(1f-.02f*progress));}
     if(elapsed[0]>=EXIT_MS){if(finishExit!=null)finishExit.run();}
     else android.view.Choreographer.getInstance().postFrameCallback(exitFrame);
    };
    android.view.Choreographer.getInstance().postFrameCallback(exitFrame);
   };
   handler.postDelayed(()->{
    if(disposed||token!=generation)return;
    final int[] draws={0};
    warmDraw=()->{
     if(++draws[0]>=2){viewport.post(()->{clearWarmDraw();viewport.postOnAnimation(begin);});}
     else viewport.postOnAnimation(viewport::invalidate);
    };
    viewport.getViewTreeObserver().addOnDrawListener(warmDraw);viewport.invalidate();
    // Fallback for a vendor view hierarchy that coalesces invalidations. No minimum delay.
    handler.postDelayed(begin,900L);
   },delay);
  }
  private void clearWarmDraw(){
   if(warmDraw!=null){if(viewport.getViewTreeObserver().isAlive())viewport.getViewTreeObserver().removeOnDrawListener(warmDraw);warmDraw=null;}
  }
  void finishHidden(){if(contentReady&&finishExit!=null)finishExit.run();}
  void ready(){
   if(disposed||finishing)return;finishing=true;handler.removeCallbacks(watchdog);
   boolean wasHeld=!released;
   web.setVisibility(android.view.View.VISIBLE);web.setAlpha(1f);
   // Keep WebView stationary. Only the native cover fades, after a prepared content frame.
   if(wasHeld||!motion())removeLayer();
   else{
    long elapsed=markStarted==0?180L:android.os.SystemClock.uptimeMillis()-markStarted;
    animateExit(layer,mark,Math.max(0L,140L-elapsed),this::removeLayer,"legacy");
   }
   transitionTheme();
   releaseDraw();
  }
  private void transitionTheme(){
   int target=Color.parseColor(systemColor.isEmpty()?"#214f4c":systemColor);
   viewport.setBackgroundColor(target);
   if(!motion()){applySystemTheme();return;}
   // Read the current bar colour first: applying the target before the tween causes a flash.
   int from=getWindow().getStatusBarColor();
   themeTransition=true;
   bars=android.animation.ValueAnimator.ofArgb(from,target);bars.setDuration(EXIT_MS);bars.setInterpolator(ease);
   bars.addUpdateListener(a->{if(!disposed){int c=(int)a.getAnimatedValue();getWindow().setStatusBarColor(c);getWindow().setNavigationBarColor(c);}});
   bars.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator a){themeTransition=false;if(!disposed)applySystemTheme();}});bars.start();
  }
  private void removeLayer(){
   stopMark();
   if(layer!=null){layer.setVisibility(android.view.View.GONE);layer.setClickable(false);viewport.removeView(layer);}
   if(!disposed&&contentReady&&android.os.Build.VERSION.SDK_INT<31){web.getSettings().setOffscreenPreRaster(false);web.evaluateJavascript("delete document.documentElement.dataset.nativeLaunching",null);}
  }
  void fail(){
   if(disposed||contentReady||finishing)return;
   handler.removeCallbacks(watchdog);stopMark();mark.setVisibility(android.view.View.GONE);
   if(failure==null){
    failure=new android.widget.LinearLayout(MainActivity.this);failure.setOrientation(android.widget.LinearLayout.VERTICAL);
    failure.setPadding(dp(24),dp(24),dp(24),dp(24));
    android.graphics.drawable.GradientDrawable card=new android.graphics.drawable.GradientDrawable();
    card.setColor(launchColor("luke_launch_inner"));card.setCornerRadius(dp(28));failure.setBackground(card);
    android.widget.TextView title=new android.widget.TextView(MainActivity.this);title.setText("启动暂未完成");title.setTextSize(20);title.setTextColor(launchColor("luke_launch_key"));
    android.widget.TextView note=new android.widget.TextView(MainActivity.this);note.setText("可以重试打开，本地聊天和手记不会被清除。");note.setTextSize(15);note.setTextColor(launchColor("luke_launch_key"));note.setPadding(0,dp(12),0,dp(20));
    android.widget.Button retry=new android.widget.Button(MainActivity.this);retry.setText("重新打开");retry.setTextColor(launchColor("luke_launch_key"));retry.setAllCaps(false);retry.setMinHeight(dp(48));
    android.graphics.drawable.GradientDrawable pill=new android.graphics.drawable.GradientDrawable();pill.setColor(launchColor("luke_launch_disc"));pill.setCornerRadius(dp(24));retry.setBackground(pill);retry.setPadding(dp(20),dp(12),dp(20),dp(12));
    retry.setOnClickListener(v->retry());failure.addView(title);failure.addView(note);failure.addView(retry,new android.widget.LinearLayout.LayoutParams(-1,-2));
    android.widget.FrameLayout.LayoutParams lp=new android.widget.FrameLayout.LayoutParams(-1,-2,android.view.Gravity.CENTER);lp.setMargins(dp(24),dp(24),dp(24),dp(24));layer.addView(failure,lp);
   }
   failure.setVisibility(android.view.View.VISIBLE);layer.setContentDescription(null);
   layer.announceForAccessibility("启动暂未完成，可以重试，本地记录不会被清除。");releaseDraw();
  }
  private void retry(){
   if(disposed||contentReady)return;
   generation++;readyPosted=false;failure.setVisibility(android.view.View.GONE);mark.setVisibility(android.view.View.VISIBLE);
   layer.setContentDescription("四时与你，正在重新打开");startMark();web.stopLoading();web.loadUrl(ORIGIN+"/");
   handler.removeCallbacks(watchdog);handler.postDelayed(watchdog,FAILURE_MS);
  }
  void dispose(){
   disposed=true;handler.removeCallbacksAndMessages(null);releaseDraw();clearWarmDraw();
   if(exitFrame!=null){android.view.Choreographer.getInstance().removeFrameCallback(exitFrame);exitFrame=null;}
   finishExit=null;
   if(bars!=null)bars.cancel();
   if(layer!=null)layer.animate().cancel();
   if(mark!=null){mark.animate().cancel();stopMark();}
   finishPlatform();
  }
 }
 @android.annotation.TargetApi(31)
 private static final class PlatformSplash {
  static void install(Activity activity,SoftStartup owner){
   activity.getSplashScreen().setOnExitAnimationListener(view->{
    long remaining=0;
    java.time.Instant start=view.getIconAnimationStart();java.time.Duration duration=view.getIconAnimationDuration();
    if(start!=null&&duration!=null)remaining=Math.max(0L,start.toEpochMilli()+duration.toMillis()-System.currentTimeMillis());
    owner.systemExit(view,view.getIconView(),view::remove,remaining);
   });
  }
 }

 public class Bridge {
  @JavascriptInterface public String defaultModel(){try(InputStream in=getAssets().open("personal-model.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[1024];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);return out.toString("UTF-8");}catch(Exception ignored){return "{}";}}
  @JavascriptInterface public void haptic(){touchFeedback("press");}
  @JavascriptInterface public void hapticEvent(String kind){touchFeedback(kind);}
  @JavascriptInterface public void feedbackEnabled(boolean enabled){runOnUiThread(()->{if(isDestroyed())return;feedbackAllowed=enabled;uiPreferences().edit().putBoolean("feedbackEnabled",enabled).apply();});}
  @JavascriptInterface public void startupAppearance(String season,String period){
   if(!Arrays.asList("auto","spring","summer","autumn","winter").contains(season)||!Arrays.asList("auto","清晨","早上","正午","午后","傍晚","夜晚","深夜").contains(period))return;
   runOnUiThread(()->{
    if(isDestroyed())return;
    android.content.SharedPreferences prefs=uiPreferences();
    boolean changed=!season.equals(prefs.getString("season","auto"))||!period.equals(prefs.getString("period","auto"));
    if(changed)prefs.edit().putString("season",season).putString("period",period).apply();
    String previous=launchStyle;configureLaunch();
    if(startup!=null&&!previous.equals(launchStyle))startup.appearanceUpdated();
   });
  }
  @JavascriptInterface public void pageReady(){
   runOnUiThread(()->{if(isDestroyed()||readyPosted)return;readyPosted=true;
    final int generation=startup.generation;
    web.postVisualStateCallback(generation,new WebView.VisualStateCallback(){@Override public void onComplete(long id){if(isDestroyed()||generation!=startup.generation||contentReady)return;contentReady=true;publishKeyboard();startup.ready();}});
   });
  }
  @JavascriptInterface public void systemTheme(String color,boolean dark){
   if(color==null||!color.matches("#[0-9a-fA-F]{6}"))return;
   runOnUiThread(()->{if(isDestroyed()||(color.equals(systemColor)&&dark==darkSystemBars))return;systemColor=color;darkSystemBars=dark;if(contentReady&&!startup.themeTransition)applySystemTheme();});
  }

  @JavascriptInterface public void geocode(String id,double lat,double lon){
   if(id==null||id.length()>80||Double.isNaN(lat)||Double.isNaN(lon)||Math.abs(lat)>90||Math.abs(lon)>180)return;
   workers.execute(()->{String place="";try{
    android.location.Geocoder g=new android.location.Geocoder(MainActivity.this,Locale.SIMPLIFIED_CHINESE);
    java.util.List<android.location.Address> result=g.getFromLocation(lat,lon,1);
    if(result!=null&&!result.isEmpty()){android.location.Address a=result.get(0);String district="";
     for(String part:new String[]{a.getSubAdminArea(),a.getLocality(),a.getSubLocality()})if(part!=null&&part.matches(".*[区县旗]$")&&!part.endsWith("自治区")&&!part.endsWith("社区")&&!part.endsWith("小区")){district=part;break;}
     if(!district.isEmpty()){String city=a.getLocality();place=city!=null&&!city.equals(district)?city+" · "+district:district;}
    }
   }catch(Exception ignored){}
   String script="window.__lukeGeocode&&window.__lukeGeocode("+JSONObject.quote(id)+","+JSONObject.quote(place)+")";
   runOnUiThread(()->{if(!isDestroyed())web.evaluateJavascript(script,null);});
   });
  }

  @JavascriptInterface public void request(String id,String address,String method,String headers,String body){perform(id,address,method,headers,body,false);}
  @JavascriptInterface public void requestStream(String id,String address,String method,String headers,String body){perform(id,address,method,headers,body,true);}
  private void perform(String id,String address,String method,String headers,String body,boolean streaming){
   if(id==null||id.length()>80||active.size()>=6)return;active.add(id);
   workers.execute(()->{HttpURLConnection c=null;try{
    URL url=new URL(address);if(!"https".equals(url.getProtocol())||url.getUserInfo()!=null||url.getHost().isEmpty()||(!method.equals("GET")&&!method.equals("POST"))||body.length()>600000||headers.length()>12000)throw new IOException();
    c=(HttpURLConnection)url.openConnection();requests.put(id,c);if(!active.contains(id))return;
    c.setInstanceFollowRedirects(false);c.setConnectTimeout(20000);c.setReadTimeout(80000);c.setRequestMethod(method);c.setRequestProperty("User-Agent","FourSeasonsLuke/1.4 (com.luke.summer)");c.setRequestProperty("Accept",streaming?"text/event-stream":"application/json");
    JSONObject h=new JSONObject(headers);for(Iterator<String> it=h.keys();it.hasNext();){String key=it.next();if(key.equalsIgnoreCase("authorization")||key.equalsIgnoreCase("content-type"))c.setRequestProperty(key,h.getString(key));}
    if(method.equals("POST")){c.setDoOutput(true);try(OutputStream out=c.getOutputStream()){out.write(body.getBytes(StandardCharsets.UTF_8));}}
    int status=c.getResponseCode();InputStream stream=status>=400?c.getErrorStream():c.getInputStream();if(streaming){String type=c.getContentType();streamPart(id,status,type,"",false);if(stream!=null)try(Reader reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){char[] chars=new char[1024];int n,total=0;while((n=reader.read(chars))!=-1){if(!active.contains(id))return;total+=n;if(total>2000000)throw new IOException();streamPart(id,status,type,new String(chars,0,n),false);}}streamPart(id,status,type,"",true);return;}ByteArrayOutputStream bytes=new ByteArrayOutputStream();if(stream!=null)try(InputStream input=stream){byte[] buf=new byte[8192];int n;while((n=input.read(buf))!=-1){if(bytes.size()+n>2000000)throw new IOException();bytes.write(buf,0,n);}}
    deliver(id,status,bytes.toString("UTF-8"));
   }catch(Exception e){if(streaming)streamPart(id,0,"","",true);else deliver(id,0,"");}finally{requests.remove(id);if(c!=null)c.disconnect();}});
  }
  @JavascriptInterface public void cancel(String id){active.remove(id);HttpURLConnection c=requests.remove(id);if(c!=null)c.disconnect();}
  @JavascriptInterface public void saveBackup(String name,String text){if(text==null||text.length()>16000000){toast("备份过大，暂时无法导出");return;}runOnUiThread(()->{if(exportText!=null){toast("请先完成当前导出");return;}exportText=text;try{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,name.replaceAll("[\\\\/:*?\"<>|]","_"));startActivityForResult(i,11);}catch(Exception e){exportText=null;toast("无法打开保存窗口");}});}
 }
}
