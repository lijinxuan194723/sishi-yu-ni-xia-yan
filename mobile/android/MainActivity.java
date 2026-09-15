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
 private volatile boolean rendererGone;
 private boolean canUseWeb(){return web!=null&&!rendererGone&&!isDestroyed();}
 private SoftStartup startup;
 private final WarmReturn warmReturn=new WarmReturn();
 private long pausedAt;
 private boolean externalReturn;
 @Override public void startActivityForResult(Intent intent,int request,Bundle options){super.startActivityForResult(intent,request,options);externalReturn=true;}
 @Override protected void onPause(){warmReturn.clear();if(canUseWeb()&&contentReady)pausedAt=android.os.SystemClock.uptimeMillis();super.onPause();}
 @Override protected void onResume(){
  super.onResume();
  if(canUseWeb()){
   web.onResume();
   if(contentReady&&pausedAt>0&&!externalReturn&&android.os.SystemClock.uptimeMillis()-pausedAt>=200L)warmReturn.show();
  }
  pausedAt=0;externalReturn=false;
 }
 private final class WarmReturn {
  private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
  private android.widget.FrameLayout cover;
  private android.view.Choreographer.FrameCallback frame;
  private int generation;
  private boolean fading;
  void show(){
   clear();
   if(!canUseWeb()||!contentReady||!android.animation.ValueAnimator.areAnimatorsEnabled())return;
   final int token=++generation;fading=false;
   cover=new android.widget.FrameLayout(MainActivity.this);cover.setBackgroundColor(resolvedSystemColor());cover.setAlpha(.88f);
   cover.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
   cover.setLayerType(android.view.View.LAYER_TYPE_HARDWARE,null);
   android.widget.ImageView badge=new android.widget.ImageView(MainActivity.this);badge.setImageDrawable(launchMark(false));
   int size=Math.round(144*getResources().getDisplayMetrics().density);
   cover.addView(badge,new android.widget.FrameLayout.LayoutParams(size,size,android.view.Gravity.CENTER));
   viewport.addView(cover,new android.widget.FrameLayout.LayoutParams(-1,-1));
   web.evaluateJavascript("document.documentElement.dataset.resuming='true'",null);
   android.util.Log.i("LukeReturn","show "+token);
   web.postVisualStateCallback(token,new WebView.VisualStateCallback(){
    @Override public void onComplete(long id){if(token==generation&&cover!=null)cover.postOnAnimation(()->fade(token));}
   });
   handler.postDelayed(()->fade(token),120L);
   // This guard only releases a transition which could not start. A late main
   // thread must not execute fade() and the old hard cleanup in the same frame.
   handler.postDelayed(()->{if(token==generation&&!fading)clear();},650L);
  }
  private void fade(int token){
   if(token!=generation||cover==null||fading)return;fading=true;
   final android.view.animation.Interpolator ease=new android.view.animation.PathInterpolator(.2f,0f,.2f,1f);
   final long began=android.os.SystemClock.uptimeMillis();
   final long[] previous={0L};final float[] elapsed={0f};final int[] count={0};
   android.util.Log.i("LukeReturn","fade "+token);
   frame=now->{
    if(token!=generation||cover==null)return;
    if(!canUseWeb()||!android.animation.ValueAnimator.areAnimatorsEnabled()||viewport.getWindowVisibility()!=android.view.View.VISIBLE){clear();return;}
    if(previous[0]!=0L)elapsed[0]+=Math.min(32L,Math.max(0L,(now-previous[0])/1000000L));
    previous[0]=now;count[0]++;
    cover.setAlpha(.88f*(1f-ease.getInterpolation(Math.min(1f,elapsed[0]/280f))));
    if(elapsed[0]>=280f){android.util.Log.i("LukeReturn","complete frames="+count[0]+" ms="+(android.os.SystemClock.uptimeMillis()-began));clear();}
    else android.view.Choreographer.getInstance().postFrameCallback(frame);
   };
   android.view.Choreographer.getInstance().postFrameCallback(frame);
  }
  void clear(){
   generation++;handler.removeCallbacksAndMessages(null);
   if(frame!=null){android.view.Choreographer.getInstance().removeFrameCallback(frame);frame=null;}
   if(cover==null)return;
   cover.animate().cancel();viewport.removeView(cover);cover=null;
   if(canUseWeb())web.evaluateJavascript("delete document.documentElement.dataset.resuming;window.dispatchEvent(new Event('luke-resumed'))",null);
   android.util.Log.i("LukeReturn","finished");
  }
 }
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
 private int launchColor(String name){int id=launchResource(name+"_"+launchSeason+"_"+launchMode,"color");return getColor(id!=0?id:launchResource(name,"color"));}
 private android.graphics.drawable.Drawable launchMark(boolean animated){int id=launchResource("luke_launch_"+launchSeason+(animated?"_animated":"_mark"),"drawable");return launchContext.getDrawable(id);}
 private void touchFeedback(String kind){
  if(!Arrays.asList("press","selection","confirm").contains(kind))return;
  runOnUiThread(()->{
   if(!canUseWeb()||!contentReady||!feedbackAllowed||!web.isShown()||!web.hasWindowFocus())return;
   long now=android.os.SystemClock.elapsedRealtime();if(now-lastFeedback<90L)return;lastFeedback=now;
   boolean clear="clear".equals(uiPreferences().getString("feedbackStyle","gentle"));
   int effect=clear?android.view.HapticFeedbackConstants.CONTEXT_CLICK:android.view.HapticFeedbackConstants.CLOCK_TICK;
   if("selection".equals(kind))effect=android.view.HapticFeedbackConstants.CLOCK_TICK;
   else if("confirm".equals(kind)&&android.os.Build.VERSION.SDK_INT>=30)effect=android.view.HapticFeedbackConstants.CONFIRM;
   boolean applied=web.performHapticFeedback(effect);android.util.Log.d("LukeFeedback","kind="+kind+" accepted="+applied);
  });
 }
 private android.widget.FrameLayout viewport;
 private boolean darkSystemBars=true;
 private boolean keyboardVisible=false;
 private String systemColor="";
 private int resolvedSystemColor(){return systemColor.isEmpty()?launchColor("luke_launch_background"):Color.parseColor(systemColor);}
 private boolean contentReady=false,readyPosted=false;
 private final ExecutorService workers=Executors.newFixedThreadPool(3);
 private final ConcurrentHashMap<String,HttpURLConnection> requests=new ConcurrentHashMap<>();
 private final Set<String> active=ConcurrentHashMap.newKeySet();
 private ValueCallback<Uri[]> fileCallback;
 private GeolocationPermissions.Callback locationCallback;
 private String locationOrigin,exportText;

 @Override public void onCreate(Bundle state){
  shownSplashStyle=uiPreferences().getString("cachedSplashStyle","");feedbackAllowed=uiPreferences().getBoolean("feedbackEnabled",true);configureLaunch();super.onCreate(state);
  viewport=new android.widget.FrameLayout(this);web=new WebView(this);web.setVisibility(android.view.View.VISIBLE);web.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);viewport.addView(web,new android.widget.FrameLayout.LayoutParams(-1,-1));setContentView(viewport);startup=new SoftStartup();startup.install();showSystemBars();
  if(android.os.Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);viewport.setOnApplyWindowInsetsListener((v,insets)->{if(!canUseWeb()){android.graphics.Insets safe=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());viewport.setPadding(safe.left,safe.top,safe.right,safe.bottom);return android.view.WindowInsets.CONSUMED;}setKeyboardVisible(insets.isVisible(android.view.WindowInsets.Type.ime()));android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout()|android.view.WindowInsets.Type.ime());android.widget.FrameLayout.LayoutParams lp=(android.widget.FrameLayout.LayoutParams)web.getLayoutParams();if(lp.leftMargin!=bars.left||lp.topMargin!=bars.top||lp.rightMargin!=bars.right||lp.bottomMargin!=bars.bottom){lp.setMargins(bars.left,bars.top,bars.right,bars.bottom);web.setLayoutParams(lp);}return android.view.WindowInsets.CONSUMED;});viewport.requestApplyInsets();}
  if(android.os.Build.VERSION.SDK_INT<30)viewport.getViewTreeObserver().addOnGlobalLayoutListener(()->{android.graphics.Rect frame=new android.graphics.Rect();viewport.getWindowVisibleDisplayFrame(frame);int height=viewport.getRootView().getHeight();setKeyboardVisible(height-frame.bottom>height*.2);});
  WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(true);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setGeolocationEnabled(true);s.setSupportMultipleWindows(false);
  web.addJavascriptInterface(new Bridge(),"LukeAndroid");
  web.setWebViewClient(new WebViewClient(){
   @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest r){
    Uri u=r.getUrl();if(!ORIGIN.equals(u.getScheme()+"://"+u.getAuthority()))return denied();String path=u.getPath();if(path==null||path.contains("..")||path.contains("\\"))return denied();if(path.equals("/"))path="/index.html";
    try{String mime=path.endsWith(".js")?"application/javascript":path.endsWith(".css")?"text/css":path.endsWith(".html")?"text/html":path.endsWith(".jpg")?"image/jpeg":path.endsWith(".png")?"image/png":path.endsWith(".svg")?"image/svg+xml":path.endsWith(".webp")?"image/webp":"application/octet-stream";return new WebResourceResponse(mime,"UTF-8",getAssets().open("web"+path));}catch(IOException e){return denied();}
   }
   @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){return rendererTerminated(view,detail.didCrash());}
   @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(canUseWeb()&&request.isForMainFrame()&&!contentReady)startup.fail();}
   @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest r){Uri u=r.getUrl();if((ORIGIN+"/").equals(u.toString()))return false;if(r.isForMainFrame()&&"https".equals(u.getScheme()))try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception ignored){}return true;}
  });
  web.setWebChromeClient(new WebChromeClient(){
   @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams p){
    if(fileCallback!=null)fileCallback.onReceiveValue(null);fileCallback=callback;
    try{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,10);}catch(Exception e){fileCallback.onReceiveValue(null);fileCallback=null;toast("无法打开文件选择器");}return true;
   }
   @Override public void onGeolocationPermissionsShowPrompt(String origin,GeolocationPermissions.Callback callback){
    if(!origin.equals(ORIGIN)&&!origin.equals(ORIGIN+"/")){callback.invoke(origin,false,false);return;}
    if(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED){callback.invoke(origin,true,false);return;}
    locationCallback=callback;locationOrigin=origin;externalReturn=true;requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION},12);
   }
  });web.loadUrl(ORIGIN+"/");
 }
 private void publishKeyboard(){if(!canUseWeb())return;web.evaluateJavascript("document.documentElement.dataset.keyboard='"+keyboardVisible+"'",null);}
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
  if(request==11){String text=exportText;exportText=null;if(result==RESULT_OK&&data!=null&&text!=null){Uri uri=data.getData();workers.execute(()->{try{try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException();out.write(text.getBytes(StandardCharsets.UTF_8));}runOnUiThread(()->{if(canUseWeb())web.evaluateJavascript("localStorage.setItem('luke-backup-confirmed',Date.now().toString());window.dispatchEvent(new Event('luke-backup-saved'))",null);});toast("备份已保存");}catch(Exception e){toast("备份未保存，请重试");}});}}
 }
 @Override public boolean dispatchKeyEvent(android.view.KeyEvent event){if(event.getKeyCode()==android.view.KeyEvent.KEYCODE_BACK){if(event.getAction()==android.view.KeyEvent.ACTION_UP&&!event.isCanceled())onBackPressed();return true;}return super.dispatchKeyEvent(event);}
 @Override public void onBackPressed(){if(!canUseWeb()||!contentReady){moveTaskToBack(true);return;}if(keyboardVisible){((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(web.getWindowToken(),0);web.evaluateJavascript("document.activeElement instanceof HTMLElement&&document.activeElement.blur()",null);return;}web.evaluateJavascript("!!(window.__lukeBack&&window.__lukeBack())",handled->{if(!"true".equals(handled))moveTaskToBack(true);});}
 @Override protected void onStop(){warmReturn.clear();if(canUseWeb())web.onPause();if(startup!=null)startup.finishHidden();super.onStop();}
 @Override protected void onDestroy(){warmReturn.clear();if(startup!=null)startup.dispose();for(HttpURLConnection c:requests.values())c.disconnect();workers.shutdownNow();if(web!=null){web.removeJavascriptInterface("LukeAndroid");web.destroy();web=null;}super.onDestroy();}
 private void deliver(String id,int status,String body){if(!active.remove(id))return;String script="window.__lukeNetwork&&window.__lukeNetwork("+JSONObject.quote(id)+","+status+","+JSONObject.quote(body)+")";runOnUiThread(()->{if(canUseWeb())web.evaluateJavascript(script,null);});}
 private void streamPart(String id,int status,String type,String text,boolean done){if(!active.contains(id))return;if(done)active.remove(id);String script="window.__lukeStreaming&&window.__lukeStreaming("+JSONObject.quote(id)+","+status+","+JSONObject.quote(type)+","+JSONObject.quote(text)+","+done+")";runOnUiThread(()->{if(canUseWeb())web.evaluateJavascript(script,null);});}
 private void applySystemTheme(){boolean dark=darkSystemBars;int value=resolvedSystemColor();viewport.setBackgroundColor(value);getWindow().setStatusBarColor(value);getWindow().setNavigationBarColor(value);if(android.os.Build.VERSION.SDK_INT>=30){android.view.WindowInsetsController c=getWindow().getInsetsController();if(c!=null){int mask=android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;c.setSystemBarsAppearance(dark?0:mask,mask);}}else getWindow().getDecorView().setSystemUiVisibility(dark?0:android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);}
 private boolean rendererTerminated(WebView view,boolean crashed){
  android.util.Log.e("LukeRenderer","terminated crashed="+crashed);
  if(view!=web){android.view.ViewParent parent=view.getParent();if(parent instanceof android.view.ViewGroup)((android.view.ViewGroup)parent).removeView(view);view.destroy();return true;}
  rendererGone=true;contentReady=false;readyPosted=true;warmReturn.clear();keyboardVisible=false;if(startup!=null)startup.dispose();
  active.clear();for(HttpURLConnection connection:requests.values())connection.disconnect();requests.clear();fileCallback=null;locationCallback=null;
  viewport.removeView(view);view.removeJavascriptInterface("LukeAndroid");view.destroy();web=null;viewport.removeAllViews();if(isDestroyed()||isFinishing())return true;
  int pad=Math.round(24*getResources().getDisplayMetrics().density);android.widget.ScrollView scroll=new android.widget.ScrollView(this);scroll.setFillViewport(true);
  android.widget.LinearLayout panel=new android.widget.LinearLayout(this);panel.setOrientation(android.widget.LinearLayout.VERTICAL);panel.setGravity(android.view.Gravity.CENTER);panel.setPadding(pad,pad,pad,pad);
  android.widget.LinearLayout card=new android.widget.LinearLayout(this);card.setOrientation(android.widget.LinearLayout.VERTICAL);card.setPadding(pad,pad,pad,pad);
  android.graphics.drawable.GradientDrawable surface=new android.graphics.drawable.GradientDrawable();surface.setColor(launchColor("luke_launch_inner"));surface.setCornerRadius(pad);card.setBackground(surface);
  android.widget.TextView title=new android.widget.TextView(this);title.setText("页面显示已中断");title.setTextSize(20);title.setTextColor(launchColor("luke_launch_key"));
  android.widget.TextView note=new android.widget.TextView(this);note.setText("已保存的聊天、手记和设置仍保留在本机，未保存的输入可能需要重新填写。点击下方按钮重新打开，不会清空本地记录。");note.setTextSize(15);note.setTextColor(launchColor("luke_launch_key"));note.setPadding(0,pad/2,0,pad);
  android.widget.Button retry=new android.widget.Button(this);retry.setText("重新载入页面");retry.setAllCaps(false);retry.setMinHeight(pad*2);retry.setTextColor(launchColor("luke_launch_key"));
  android.graphics.drawable.GradientDrawable button=new android.graphics.drawable.GradientDrawable();button.setColor(launchColor("luke_launch_disc"));button.setCornerRadius(pad);retry.setBackground(button);retry.setOnClickListener(v->{retry.setEnabled(false);recreate();});
  android.widget.Button leave=new android.widget.Button(this);leave.setText("暂时返回桌面");leave.setAllCaps(false);leave.setMinHeight(pad*2);leave.setOnClickListener(v->moveTaskToBack(true));
  card.addView(title);card.addView(note);card.addView(retry,new android.widget.LinearLayout.LayoutParams(-1,-2));card.addView(leave,new android.widget.LinearLayout.LayoutParams(-1,-2));
  panel.addView(card,new android.widget.LinearLayout.LayoutParams(-1,-2));scroll.addView(panel,new android.widget.ScrollView.LayoutParams(-1,-2));viewport.addView(scroll,new android.widget.FrameLayout.LayoutParams(-1,-1));viewport.setBackgroundColor(launchColor("luke_launch_background"));
  if(android.os.Build.VERSION.SDK_INT>=30)viewport.requestApplyInsets();title.announceForAccessibility("页面显示已中断，可以重新载入，已保存记录不会清除。");return true;
 }
 private void actionResult(String id,boolean ok,String message,String route){if(!canUseWeb())return;try{JSONObject detail=new JSONObject();detail.put("id",id);detail.put("ok",ok);detail.put("message",message);detail.put("route",route);web.evaluateJavascript("window.dispatchEvent(new CustomEvent('luke-action-result',{detail:"+detail.toString()+"}))",null);}catch(Exception ignored){}}
 private String officialSongLink(String text,boolean qq){if(text==null||text.length()>2000)return "";try{Uri u=Uri.parse(text.trim());String host=u.getHost();if(!"https".equals(u.getScheme())||u.getUserInfo()!=null||u.getPort()!=-1)return "";if(qq?!"y.qq.com".equals(host):!"music.163.com".equals(host))return "";return u.toString();}catch(RuntimeException e){return "";}}
 private void openMusic(String id,String title,String artist,String qq,String netease){
  if(!canUseWeb())return;String query=(title+" "+artist).trim();String[] packages={"com.tencent.qqmusic","com.netease.cloudmusic"};String[] names={"QQ 音乐","网易云音乐"};String[] links={officialSongLink(qq,true),officialSongLink(netease,false)};
  for(int n=0;n<packages.length;n++){
   try{getPackageManager().getPackageInfo(packages[n],0);}catch(PackageManager.NameNotFoundException ignored){continue;}
   String search=n==0?"https://y.qq.com/n/ryqq/search?w="+Uri.encode(query):"https://music.163.com/#/search/m/?s="+Uri.encode(query);
   try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(links[n].isEmpty()?search:links[n])).setPackage(packages[n]));actionResult(id,true,"已请求在"+names[n]+(links[n].isEmpty()?"中查找歌名和歌手，请核对版本。":"打开绑定的单曲，请核对后播放。"),packages[n]);return;}catch(RuntimeException ignored){}
   try{Intent launch=getPackageManager().getLaunchIntentForPackage(packages[n]);if(launch!=null){((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("歌名和歌手",query));startActivity(launch);actionResult(id,true,"已打开"+names[n]+"，歌名和歌手已复制，请粘贴搜索。该版本不支持直达请求。",packages[n]);return;}}catch(RuntimeException ignored){}
  }
  try{String url=!links[0].isEmpty()?links[0]:!links[1].isEmpty()?links[1]:"https://y.qq.com/n/ryqq/search?w="+Uri.encode(query);startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));actionResult(id,true,"未找到可处理的音乐应用，已请求打开官方网页。","web");}catch(RuntimeException error){actionResult(id,false,"无法打开音乐应用或浏览器，可以复制歌名后手动查找。","none");}
 }
 private final class SoftStartup {
  private static final long EXIT_MS=380L, FAILURE_MS=8000L;
  private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
  private final Runnable watchdog=()->fail();
  private final android.view.animation.Interpolator ease=new android.view.animation.PathInterpolator(.4f,0f,.2f,1f);
  private android.widget.FrameLayout layer;
  private android.widget.ImageView mark;
  private android.widget.LinearLayout failure;
  private android.animation.ValueAnimator bars;
  private Runnable removePlatform;
  private android.view.View platformView,platformIcon;
  private long platformIntroRemaining;
  private boolean platformExiting,nativeExitStarted;
  private android.view.ViewTreeObserver.OnDrawListener warmDraw;
  private android.view.Choreographer.FrameCallback exitFrame;
  private Runnable finishExit;
  private long markStarted;
  private boolean themeTransition;
  private boolean disposed,finishing;
  private int background,generation;
  private int resource(String name,String type){int id=getResources().getIdentifier(name,type,getPackageName());if(id==0)throw new IllegalStateException("Missing startup resource: "+name);return id;}
  private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
  private boolean motion(){return android.animation.ValueAnimator.areAnimatorsEnabled();}
  void install(){
   background=launchColor("luke_launch_background");android.util.Log.i("LukeSeason","launch season="+launchSeason+" mode="+launchMode);darkSystemBars="night".equals(launchMode);
   viewport.setBackgroundColor(background);web.setBackgroundColor(background);getWindow().setStatusBarColor(background);getWindow().setNavigationBarColor(background);
   layer=new android.widget.FrameLayout(MainActivity.this);layer.setBackgroundColor(background);layer.setClickable(true);layer.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES);layer.setContentDescription("四时与你，正在打开");
   mark=new android.widget.ImageView(MainActivity.this);mark.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);mark.setImageDrawable(launchMark(false));layer.addView(mark,new android.widget.FrameLayout.LayoutParams(dp(288),dp(288),android.view.Gravity.CENTER));viewport.addView(layer,new android.widget.FrameLayout.LayoutParams(-1,-1));
   if(android.os.Build.VERSION.SDK_INT>=31)PlatformSplash.install(MainActivity.this,this);else{mark.postOnAnimation(()->{if(!disposed&&!contentReady)startMark();});}handler.postDelayed(watchdog,FAILURE_MS);
  }
  private void startMark(){markStarted=android.os.SystemClock.uptimeMillis();if(!motion()){mark.setImageDrawable(launchMark(false));return;}mark.setImageDrawable(launchMark(true));android.graphics.drawable.Drawable d=mark.getDrawable();if(d instanceof android.graphics.drawable.Animatable)((android.graphics.drawable.Animatable)d).start();}
  private void stopMark(){android.graphics.drawable.Drawable d=mark.getDrawable();if(d instanceof android.graphics.drawable.Animatable)((android.graphics.drawable.Animatable)d).stop();}
  void systemExit(android.view.View splash,android.view.View icon,Runnable remove,long introRemaining){
   if(disposed||nativeExitStarted){remove.run();return;}removePlatform=remove;platformView=splash;platformIcon=seasonalSystemIcon(splash,icon);platformIntroRemaining=introRemaining;
   android.util.Log.i("LukeMotion","system surface-attached contentReady="+contentReady);if(contentReady)startSystemExit();else if(failure!=null&&failure.getVisibility()==android.view.View.VISIBLE)finishPlatform();
  }
  private void startSystemExit(){if(disposed||platformExiting||platformView==null||!contentReady)return;platformExiting=true;if(!motion()){removeLayer();finishPlatform();return;}long delay=Math.min(180L,Math.max(0L,platformIntroRemaining-EXIT_MS));animateExit(platformView,platformIcon,delay,this::finishPlatform,"system");}
  private android.widget.ImageView seasonalOverlay;
  private android.view.View seasonalOriginal;
  private int seasonalFrom;
  private android.view.View seasonalSystemIcon(android.view.View splash,android.view.View icon){
   if(launchStyle.equals(shownSplashStyle)||!(splash instanceof android.widget.FrameLayout)||icon==null)return icon;android.graphics.drawable.Drawable bg=splash.getBackground();seasonalFrom=bg instanceof android.graphics.drawable.ColorDrawable?((android.graphics.drawable.ColorDrawable)bg).getColor():background;
   seasonalOriginal=icon;seasonalOverlay=new android.widget.ImageView(MainActivity.this);seasonalOverlay.setImageDrawable(launchMark(false));seasonalOverlay.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
   int width=icon.getWidth(),height=icon.getHeight();if(width<1||height<1){width=dp(288);height=dp(288);}android.widget.FrameLayout.LayoutParams lp=new android.widget.FrameLayout.LayoutParams(width,height);lp.leftMargin=icon.getLeft();lp.topMargin=icon.getTop();seasonalOverlay.setAlpha(0f);((android.widget.FrameLayout)splash).addView(seasonalOverlay,lp);return seasonalOverlay;
  }
  private void blendSeasonalSystem(float progress){
   if(seasonalOverlay==null||platformView==null)return;float blend=ease.getInterpolation(Math.min(1f,progress));seasonalOverlay.setAlpha(blend);if(seasonalOriginal!=null)seasonalOriginal.setAlpha(1f-blend);int target=launchColor("luke_launch_background");
   int c=Color.rgb(Math.round(Color.red(seasonalFrom)+(Color.red(target)-Color.red(seasonalFrom))*blend),Math.round(Color.green(seasonalFrom)+(Color.green(target)-Color.green(seasonalFrom))*blend),Math.round(Color.blue(seasonalFrom)+(Color.blue(target)-Color.blue(seasonalFrom))*blend));platformView.setBackgroundColor(c);
  }
  void appearanceUpdated(){
   if(disposed||contentReady||finishing)return;int color=launchColor("luke_launch_background");background=color;layer.setBackgroundColor(color);viewport.setBackgroundColor(color);web.setBackgroundColor(color);android.graphics.drawable.Drawable before=mark.getDrawable();
   android.graphics.drawable.TransitionDrawable blend=new android.graphics.drawable.TransitionDrawable(new android.graphics.drawable.Drawable[]{before,launchMark(false)});mark.setImageDrawable(blend);blend.setCrossFadeEnabled(true);blend.startTransition(motion()?160:0);android.util.Log.i("LukeSeason","resolved season="+launchSeason+" mode="+launchMode);
  }
  private void finishPlatform(){
   seasonalOverlay=null;seasonalOriginal=null;if(platformView!=null){platformView.animate().cancel();platformView=null;platformIcon=null;}if(removePlatform!=null){Runnable remove=removePlatform;removePlatform=null;remove.run();}
   if(!disposed&&contentReady){web.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);web.evaluateJavascript("delete document.documentElement.dataset.nativeLaunching",null);}
  }
  private void animateExit(android.view.View surface,android.view.View icon,long delay,Runnable remove,String name){
   final int token=generation;final boolean[] started={false};Runnable begin=()->{
    if(started[0]||disposed||token!=generation)return;started[0]=true;clearWarmDraw();if(surface==platformView&&contentReady)removeLayer();if(!motion()){remove.run();return;}
    final int oldLayer=surface.getLayerType();surface.setLayerType(android.view.View.LAYER_TYPE_HARDWARE,null);surface.buildLayer();final float fromAlpha=surface.getAlpha(),fromX=icon==null?1f:icon.getScaleX(),fromY=icon==null?1f:icon.getScaleY();
    final long began=android.os.SystemClock.uptimeMillis();final long[] previous={0L},maxGap={0L};final float[] elapsed={0f};final int[] frames={0};final boolean[] ended={false};
    finishExit=()->{if(ended[0])return;ended[0]=true;if(exitFrame!=null){android.view.Choreographer.getInstance().removeFrameCallback(exitFrame);exitFrame=null;}surface.setAlpha(0f);surface.setLayerType(oldLayer,null);finishExit=null;android.util.Log.i("LukeMotion",name+" exit-complete ms="+(android.os.SystemClock.uptimeMillis()-began)+" frames="+frames[0]+" maxGapMs="+maxGap[0]);remove.run();};
    android.util.Log.i("LukeMotion",name+" exit-start");
    exitFrame=now->{
     if(disposed||token!=generation)return;if(!motion()||viewport.getWindowVisibility()!=android.view.View.VISIBLE){if(finishExit!=null)finishExit.run();return;}
     if(previous[0]!=0){long gap=Math.max(0L,(now-previous[0])/1000000L);maxGap[0]=Math.max(maxGap[0],gap);elapsed[0]+=Math.min(32L,gap);}previous[0]=now;frames[0]++;
     float progress=ease.getInterpolation(Math.min(1f,elapsed[0]/EXIT_MS));if(surface==platformView)blendSeasonalSystem(elapsed[0]/180f);surface.setAlpha(fromAlpha*(1f-progress));if(icon!=null){icon.setScaleX(fromX*(1f-.02f*progress));icon.setScaleY(fromY*(1f-.02f*progress));}
     if(elapsed[0]>=EXIT_MS){if(finishExit!=null)finishExit.run();}else android.view.Choreographer.getInstance().postFrameCallback(exitFrame);
    };android.view.Choreographer.getInstance().postFrameCallback(exitFrame);
   };
   handler.postDelayed(()->{if(disposed||token!=generation)return;final int[] draws={0};warmDraw=()->{if(++draws[0]>=2){viewport.post(()->{clearWarmDraw();viewport.postOnAnimation(begin);});}else viewport.postOnAnimation(viewport::invalidate);};viewport.getViewTreeObserver().addOnDrawListener(warmDraw);viewport.invalidate();handler.postDelayed(begin,900L);},delay);
  }
  private void clearWarmDraw(){if(warmDraw!=null){if(viewport.getViewTreeObserver().isAlive())viewport.getViewTreeObserver().removeOnDrawListener(warmDraw);warmDraw=null;}}
  void finishHidden(){if(contentReady&&finishExit!=null)finishExit.run();}
  void ready(){
   if(disposed||finishing)return;finishing=true;handler.removeCallbacks(watchdog);web.setVisibility(android.view.View.VISIBLE);web.setAlpha(1f);android.util.Log.i("LukeMotion","content frame-prepared");transitionTheme();
   if(platformView!=null)startSystemExit();else{nativeExitStarted=true;if(!motion())removeLayer();else{long elapsed=markStarted==0?180L:android.os.SystemClock.uptimeMillis()-markStarted;animateExit(layer,mark,Math.max(0L,140L-elapsed),this::removeLayer,"legacy");}}
  }
  private void transitionTheme(){
   int target=resolvedSystemColor();viewport.setBackgroundColor(target);if(!motion()){applySystemTheme();return;}int from=getWindow().getStatusBarColor();themeTransition=true;
   bars=android.animation.ValueAnimator.ofArgb(from,target);bars.setDuration(EXIT_MS);bars.setInterpolator(ease);bars.addUpdateListener(a->{if(!disposed){int c=(int)a.getAnimatedValue();getWindow().setStatusBarColor(c);getWindow().setNavigationBarColor(c);}});
   bars.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator a){themeTransition=false;if(!disposed)applySystemTheme();}});bars.start();
  }
  private void removeLayer(){stopMark();if(layer!=null){layer.setVisibility(android.view.View.GONE);layer.setClickable(false);viewport.removeView(layer);}if(!disposed&&contentReady&&platformView==null){web.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);web.evaluateJavascript("delete document.documentElement.dataset.nativeLaunching",null);}}
  void fail(){
   if(disposed||contentReady||finishing)return;handler.removeCallbacks(watchdog);stopMark();mark.setVisibility(android.view.View.GONE);
   if(failure==null){
    failure=new android.widget.LinearLayout(MainActivity.this);failure.setOrientation(android.widget.LinearLayout.VERTICAL);failure.setPadding(dp(24),dp(24),dp(24),dp(24));android.graphics.drawable.GradientDrawable card=new android.graphics.drawable.GradientDrawable();card.setColor(launchColor("luke_launch_inner"));card.setCornerRadius(dp(28));failure.setBackground(card);
    android.widget.TextView title=new android.widget.TextView(MainActivity.this);title.setText("启动暂未完成");title.setTextSize(20);title.setTextColor(launchColor("luke_launch_key"));android.widget.TextView note=new android.widget.TextView(MainActivity.this);note.setText("可以重试打开，本地聊天和手记不会被清除。");note.setTextSize(15);note.setTextColor(launchColor("luke_launch_key"));note.setPadding(0,dp(12),0,dp(20));
    android.widget.Button retry=new android.widget.Button(MainActivity.this);retry.setText("重新打开");retry.setTextColor(launchColor("luke_launch_key"));retry.setAllCaps(false);retry.setMinHeight(dp(48));android.graphics.drawable.GradientDrawable pill=new android.graphics.drawable.GradientDrawable();pill.setColor(launchColor("luke_launch_disc"));pill.setCornerRadius(dp(24));retry.setBackground(pill);retry.setPadding(dp(20),dp(12),dp(20),dp(12));
    retry.setOnClickListener(v->retry());failure.addView(title);failure.addView(note);failure.addView(retry,new android.widget.LinearLayout.LayoutParams(-1,-2));android.widget.FrameLayout.LayoutParams lp=new android.widget.FrameLayout.LayoutParams(-1,-2,android.view.Gravity.CENTER);lp.setMargins(dp(24),dp(24),dp(24),dp(24));layer.addView(failure,lp);
   }
   failure.setVisibility(android.view.View.VISIBLE);layer.setContentDescription(null);layer.announceForAccessibility("启动暂未完成，可以重试，本地记录不会被清除。");if(platformView!=null)finishPlatform();viewport.invalidate();
  }
  private void retry(){if(disposed||contentReady)return;generation++;readyPosted=false;failure.setVisibility(android.view.View.GONE);mark.setVisibility(android.view.View.VISIBLE);layer.setContentDescription("四时与你，正在重新打开");startMark();web.stopLoading();web.loadUrl(ORIGIN+"/");handler.removeCallbacks(watchdog);handler.postDelayed(watchdog,FAILURE_MS);}
  void dispose(){if(disposed)return;generation++;disposed=true;handler.removeCallbacksAndMessages(null);clearWarmDraw();if(exitFrame!=null){android.view.Choreographer.getInstance().removeFrameCallback(exitFrame);exitFrame=null;}finishExit=null;if(bars!=null)bars.cancel();if(layer!=null)layer.animate().cancel();if(mark!=null){mark.animate().cancel();stopMark();}finishPlatform();}
 }
 @android.annotation.TargetApi(31)
 private static final class PlatformSplash {
  static void install(Activity activity,SoftStartup owner){activity.getSplashScreen().setOnExitAnimationListener(view->{long remaining=0;java.time.Instant start=view.getIconAnimationStart();java.time.Duration duration=view.getIconAnimationDuration();if(start!=null&&duration!=null)remaining=Math.max(0L,start.toEpochMilli()+duration.toMillis()-System.currentTimeMillis());owner.systemExit(view,view.getIconView(),view::remove,remaining);});}
 }
 public class Bridge {
  @JavascriptInterface public String defaultModel(){try(InputStream in=getAssets().open("personal-model.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[1024];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);return out.toString("UTF-8");}catch(Exception ignored){return "{}";}}
  @JavascriptInterface public void haptic(){touchFeedback("press");}
  @JavascriptInterface public void hapticEvent(String kind){touchFeedback(kind);}
  @JavascriptInterface public void feedbackEnabled(boolean enabled){runOnUiThread(()->{if(!canUseWeb())return;feedbackAllowed=enabled;uiPreferences().edit().putBoolean("feedbackEnabled",enabled).apply();});}
  @JavascriptInterface public void startupAppearance(String season,String period){
   if(!Arrays.asList("auto","spring","summer","autumn","winter").contains(season)||!Arrays.asList("auto","清晨","早上","正午","午后","傍晚","夜晚","深夜").contains(period))return;
   runOnUiThread(()->{if(!canUseWeb())return;android.content.SharedPreferences prefs=uiPreferences();boolean changed=!season.equals(prefs.getString("season","auto"))||!period.equals(prefs.getString("period","auto"));if(changed)prefs.edit().putString("season",season).putString("period",period).apply();String previous=launchStyle;configureLaunch();if(startup!=null&&!previous.equals(launchStyle))startup.appearanceUpdated();});
  }
  @JavascriptInterface public void feedbackStyle(String style){if(Arrays.asList("gentle","clear","off").contains(style))uiPreferences().edit().putString("feedbackStyle",style).apply();}
  @JavascriptInterface public String feedbackStatus(){try{android.os.Vibrator v=(android.os.Vibrator)getSystemService(VIBRATOR_SERVICE);if(v==null||!v.hasVibrator())return "unavailable";return android.provider.Settings.System.getInt(getContentResolver(),android.provider.Settings.System.HAPTIC_FEEDBACK_ENABLED,1)==0?"disabled":"enabled";}catch(RuntimeException error){return "unknown";}}
  @JavascriptInterface public void openSong(String id,String title,String artist,String qq,String netease){if(id==null||id.length()>100||title==null||title.length()>300||artist==null||artist.length()>300)return;runOnUiThread(()->openMusic(id,title,artist,qq,netease));}
  @JavascriptInterface public void clockAction(String id,String mode,int hour,int minute,int seconds,String label){
   if(id==null||id.length()>100||label==null||label.length()>80)return;
   runOnUiThread(()->{try{Intent i;
     if("timer".equals(mode)&&seconds>=1&&seconds<=86400)i=new Intent(android.provider.AlarmClock.ACTION_SET_TIMER).putExtra(android.provider.AlarmClock.EXTRA_LENGTH,seconds);
     else if("alarm".equals(mode)&&hour>=0&&hour<=23&&minute>=0&&minute<=59)i=new Intent(android.provider.AlarmClock.ACTION_SET_ALARM).putExtra(android.provider.AlarmClock.EXTRA_HOUR,hour).putExtra(android.provider.AlarmClock.EXTRA_MINUTES,minute);
     else if("manage".equals(mode))i=new Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS);
     else{actionResult(id,false,"提醒参数不正确，请重新检查。","clock");return;}
     i.putExtra(android.provider.AlarmClock.EXTRA_MESSAGE,label).putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI,false);startActivity(i);actionResult(id,true,"已打开系统时钟，请在那里确认时间、铃声和振动；本应用不会代替系统确认。","clock");
    }catch(RuntimeException error){actionResult(id,false,"没有找到支持此操作的系统时钟，请手动打开手机时钟设置提醒。","clock");}
   });
  }
  @JavascriptInterface public void pageReady(){runOnUiThread(()->{if(!canUseWeb()||readyPosted)return;readyPosted=true;final int generation=startup.generation;web.postVisualStateCallback(generation,new WebView.VisualStateCallback(){@Override public void onComplete(long id){if(!canUseWeb()||generation!=startup.generation||contentReady)return;contentReady=true;publishKeyboard();startup.ready();}});});}
  @JavascriptInterface public void systemTheme(String color,boolean dark){if(color==null||!color.matches("#[0-9a-fA-F]{6}"))return;runOnUiThread(()->{if(!canUseWeb()||(color.equals(systemColor)&&dark==darkSystemBars))return;systemColor=color;darkSystemBars=dark;if(contentReady&&!startup.themeTransition)applySystemTheme();});}
  @JavascriptInterface public void geocode(String id,double lat,double lon){
   if(rendererGone||id==null||id.length()>80||Double.isNaN(lat)||Double.isNaN(lon)||Math.abs(lat)>90||Math.abs(lon)>180)return;
   workers.execute(()->{String place="";try{android.location.Geocoder g=new android.location.Geocoder(MainActivity.this,Locale.SIMPLIFIED_CHINESE);java.util.List<android.location.Address> result=g.getFromLocation(lat,lon,1);
    if(result!=null&&!result.isEmpty()){android.location.Address a=result.get(0);String district="";for(String part:new String[]{a.getSubAdminArea(),a.getLocality(),a.getSubLocality()})if(part!=null&&part.matches(".*[区县旗]$")&&!part.endsWith("自治区")&&!part.endsWith("社区")&&!part.endsWith("小区")){district=part;break;}if(!district.isEmpty()){String city=a.getLocality();place=city!=null&&!city.equals(district)?city+" · "+district:district;}}
   }catch(Exception ignored){}String script="window.__lukeGeocode&&window.__lukeGeocode("+JSONObject.quote(id)+","+JSONObject.quote(place)+")";runOnUiThread(()->{if(canUseWeb())web.evaluateJavascript(script,null);});});
  }
  @JavascriptInterface public void request(String id,String address,String method,String headers,String body){perform(id,address,method,headers,body,false);}
  @JavascriptInterface public void requestStream(String id,String address,String method,String headers,String body){perform(id,address,method,headers,body,true);}
  private void perform(String id,String address,String method,String headers,String body,boolean streaming){
   if(rendererGone||id==null||id.length()>80||active.size()>=6)return;active.add(id);
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
  @JavascriptInterface public void saveBackup(String name,String text){if(rendererGone||text==null||text.length()>60000000){toast("备份过大，暂时无法导出");return;}runOnUiThread(()->{if(exportText!=null){toast("请先完成当前导出");return;}exportText=text;try{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,name.replaceAll("[\\\\/:*?\"<>|]","_"));startActivityForResult(i,11);}catch(Exception e){exportText=null;toast("无法打开保存窗口");}});}
 }
}
