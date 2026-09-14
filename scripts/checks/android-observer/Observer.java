package com.luke.motion.observer;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.Bundle;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import android.accessibilityservice.AccessibilityServiceInfo;
import java.io.*;
import java.net.*;
import org.json.JSONObject;
import org.xmlpull.v1.XmlSerializer;

/** Test-only, read-only UI tree observer. One UiAutomation session per run. */
public final class Observer extends Instrumentation {
 private UiAutomation automation;
 private int port=37621;
 @Override public void onCreate(Bundle arguments){super.onCreate(arguments);port=Integer.parseInt(arguments.getString("port","37621"));start();}
 private String value(CharSequence value){return value==null?"":value.toString().replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]","");}
 private void node(XmlSerializer xml,AccessibilityNodeInfo item,int depth,int[] count) throws Exception {
  if(depth>60||++count[0]>6000)throw new IOException("Accessibility tree limit exceeded");
  Rect rect=new Rect();item.getBoundsInScreen(rect);
  xml.startTag("","node");
  xml.attribute("","text",value(item.getText()));xml.attribute("","content-desc",value(item.getContentDescription()));
  xml.attribute("","resource-id",value(item.getViewIdResourceName()));xml.attribute("","class",value(item.getClassName()));
  xml.attribute("","package",value(item.getPackageName()));xml.attribute("","clickable",String.valueOf(item.isClickable()));
  xml.attribute("","enabled",String.valueOf(item.isEnabled()));xml.attribute("","scrollable",String.valueOf(item.isScrollable()));
  xml.attribute("","bounds",item.isVisibleToUser()?rect.toShortString():"[0,0][0,0]");
  for(int i=0;i<item.getChildCount();i++){AccessibilityNodeInfo child=item.getChild(i);if(child!=null)try{node(xml,child,depth+1,count);}finally{child.recycle();}}
  xml.endTag("","node");
 }
 private String dump() throws Exception {
  if(android.os.Build.VERSION.SDK_INT>=34)automation.clearCache();
  AccessibilityNodeInfo root=automation.getRootInActiveWindow();
  if(root==null)throw new IOException("No active accessibility root");
  try{
   if(!root.refresh())throw new IOException("No active accessibility root");
   android.graphics.Point size=new android.graphics.Point();
   android.view.Display display=((android.view.WindowManager)getContext().getSystemService(android.content.Context.WINDOW_SERVICE)).getDefaultDisplay();
   display.getRealSize(size);
   StringWriter output=new StringWriter();XmlSerializer xml=android.util.Xml.newSerializer();xml.setOutput(output);
   xml.startDocument("UTF-8",true);xml.startTag("","hierarchy");xml.attribute("","rotation",String.valueOf(display.getRotation()));xml.attribute("","width",String.valueOf(size.x));xml.attribute("","height",String.valueOf(size.y));node(xml,root,0,new int[]{0});xml.endTag("","hierarchy");xml.endDocument();return output.toString();
  }finally{root.recycle();}
 }
 @Override public void onStart(){
  automation=getUiAutomation();AccessibilityServiceInfo info=automation.getServiceInfo();
  info.flags|=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS|AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
  automation.setServiceInfo(info);
  try(ServerSocket server=new ServerSocket(port,4,InetAddress.getByName("127.0.0.1"))){
   server.setSoTimeout(1200000);
   boolean done=false;
   while(!done)try(Socket client=server.accept()){
    client.setSoTimeout(10000);
    BufferedReader in=new BufferedReader(new InputStreamReader(client.getInputStream(),"UTF-8"));
    String command=in.readLine();JSONObject response=new JSONObject();
    try{
     if("dump".equals(command))response.put("xml",dump());
     else if("ping".equals(command))response.put("ready",true);
     else if("stop".equals(command)){done=true;response.put("stopped",true);}
     else throw new IOException("Unsupported read-only observer command");
    }catch(Exception error){response.put("error",error.toString());}
    byte[] bytes=response.toString().getBytes("UTF-8");DataOutputStream out=new DataOutputStream(client.getOutputStream());out.writeInt(bytes.length);out.write(bytes);out.flush();
   }
   finish(0,new Bundle());
  }catch(Exception error){Bundle failure=new Bundle();failure.putString("error",error.toString());finish(1,failure);}
 }
}
