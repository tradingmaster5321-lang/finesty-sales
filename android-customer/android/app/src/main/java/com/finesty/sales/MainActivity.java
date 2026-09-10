package com.finesty.sales;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity {
  private static final int FILE_CHOOSER_REQ = 4101;
  private static final int NOTIFICATION_REQ = 4102;
  private ValueCallback<Uri[]> filePathCallback;
  private WebView mainWebView;
  private WebView printWebView;

  @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    mainWebView = new WebView(this);
    mainWebView.setWebViewClient(new AppWebViewClient());
    mainWebView.setWebChromeClient(new AppWebChromeClient());
    WebSettings s=mainWebView.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setDatabaseEnabled(true);
    s.setAllowFileAccess(true);
    s.setAllowContentAccess(true);
    s.setBuiltInZoomControls(false);
    mainWebView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
    mainWebView.loadUrl("file:///android_asset/index.html");
    setContentView(mainWebView);
  }

  private class AppWebViewClient extends WebViewClient {
    @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request){ return openExternal(request.getUrl()); }
    @Override public boolean shouldOverrideUrlLoading(WebView view, String url){ return openExternal(Uri.parse(url)); }
    private boolean openExternal(Uri uri){
      String scheme=uri.getScheme();
      if(scheme==null) return false;
      if(scheme.equals("http") || scheme.equals("https") || scheme.equals("whatsapp") || scheme.equals("mailto") || scheme.equals("tel")){
        try{ startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
        catch(ActivityNotFoundException e){ Toast.makeText(MainActivity.this,"Hakuna app ya kufungua link hii.",Toast.LENGTH_SHORT).show(); }
        return true;
      }
      return false;
    }
  }

  private class AppWebChromeClient extends WebChromeClient {
    @Override public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params){
      if(filePathCallback!=null) filePathCallback.onReceiveValue(null);
      filePathCallback=callback;
      Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
      intent.addCategory(Intent.CATEGORY_OPENABLE);
      intent.setType("text/*");
      intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"text/csv","text/plain","application/csv","application/vnd.ms-excel","application/octet-stream"});
      try{ startActivityForResult(intent,FILE_CHOOSER_REQ); return true; }
      catch(ActivityNotFoundException e){ filePathCallback=null; Toast.makeText(MainActivity.this,"File picker haipatikani kwenye kifaa hiki.",Toast.LENGTH_LONG).show(); return false; }
    }
  }

  @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
    super.onActivityResult(requestCode,resultCode,data);
    if(requestCode==FILE_CHOOSER_REQ){
      if(filePathCallback==null)return;
      Uri[] results=null;
      if(resultCode==RESULT_OK && data!=null){ Uri u=data.getData(); if(u!=null)results=new Uri[]{u}; }
      filePathCallback.onReceiveValue(results);
      filePathCallback=null;
    }
  }

  private class AndroidBridge {
    @JavascriptInterface public void openFilePicker(){
      runOnUiThread(()->{
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"text/csv","text/plain","application/csv","application/vnd.ms-excel","application/octet-stream"});
        try{ startActivityForResult(intent,FILE_CHOOSER_REQ); }catch(Exception e){ Toast.makeText(MainActivity.this,"File picker haipatikani.",Toast.LENGTH_LONG).show(); }
      });
    }

    @JavascriptInterface public void shareWhatsApp(String message,String phone){
      try{
        String p=phone==null?"":phone.replaceAll("[^0-9]","");
        Uri uri=Uri.parse("https://wa.me/"+p+"?text="+Uri.encode(message==null?"":message));
        Intent i=new Intent(Intent.ACTION_VIEW,uri); i.setPackage("com.whatsapp");
        try{ startActivity(i); }catch(ActivityNotFoundException e){ startActivity(new Intent(Intent.ACTION_VIEW,uri)); }
      }catch(Exception e){ Toast.makeText(MainActivity.this,"Imeshindikana kufungua WhatsApp.",Toast.LENGTH_LONG).show(); }
    }

    @JavascriptInterface public void savePdf(String html,String filename){
      runOnUiThread(()->{
        printWebView=new WebView(MainActivity.this);
        WebSettings s=printWebView.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        printWebView.setVisibility(View.INVISIBLE);
        printWebView.setWebViewClient(new WebViewClient(){
          @Override public void onPageFinished(WebView view,String url){
            PrintManager pm=(PrintManager)getSystemService(PRINT_SERVICE);
            String job=(filename==null||filename.isEmpty()?"Finesty Sales Report":filename).replace(".pdf","");
            PrintDocumentAdapter adapter=view.createPrintDocumentAdapter(job);
            pm.print(job,adapter,new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).setMinMargins(PrintAttributes.Margins.NO_MARGINS).build());
          }
        });
        printWebView.loadDataWithBaseURL(null,html==null?"":html,"text/html","UTF-8",null);
      });
    }

    @JavascriptInterface public void requestPermissions(){
      if(Build.VERSION.SDK_INT>=33){
        if(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFICATION_REQ);
        else Toast.makeText(MainActivity.this,"Notifications tayari zimeruhusiwa.",Toast.LENGTH_SHORT).show();
      }else Toast.makeText(MainActivity.this,"Android hii haisemi runtime notification permission.",Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface public String getPermissionStatus(){
      String n;
      if(Build.VERSION.SDK_INT>=33) n=checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED?"Notifications: GRANTED":"Notifications: NOT GRANTED";
      else n="Notifications: SYSTEM MANAGED";
      return n+" | Files: Android File Picker (per-file access) | PDF: Android Print/Save as PDF | WhatsApp: Share/Open action";
    }
  }
}
