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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;

public class MainActivity extends Activity {
  private static final int FILE_CHOOSER_REQ=4101, PRODUCT_IMPORT_REQ=4102, NOTIFICATION_REQ=4103, STORAGE_REQ=4104;
  private ValueCallback<Uri[]> filePathCallback; private WebView mainWebView, printWebView;
  @SuppressLint({"SetJavaScriptEnabled","JavascriptInterface"})
  @Override public void onCreate(Bundle b){super.onCreate(b);mainWebView=new WebView(this);mainWebView.setWebViewClient(new AppWebViewClient());mainWebView.setWebChromeClient(new AppWebChromeClient());WebSettings s=mainWebView.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setDatabaseEnabled(true);s.setAllowFileAccess(true);s.setAllowContentAccess(true);mainWebView.addJavascriptInterface(new AndroidBridge(),"AndroidBridge");mainWebView.loadUrl("file:///android_asset/index.html");setContentView(mainWebView);}
  private class AppWebViewClient extends WebViewClient{@Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return openExternal(r.getUrl());}@Override public boolean shouldOverrideUrlLoading(WebView v,String u){return openExternal(Uri.parse(u));}private boolean openExternal(Uri u){String s=u.getScheme();if(s==null)return false;if(s.equals("http")||s.equals("https")||s.equals("whatsapp")||s.equals("mailto")||s.equals("tel")){try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception e){Toast.makeText(MainActivity.this,"Hakuna app ya kufungua link hii.",Toast.LENGTH_SHORT).show();}return true;}return false;}}
  private class AppWebChromeClient extends WebChromeClient{@Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> cb,FileChooserParams p){if(filePathCallback!=null)filePathCallback.onReceiveValue(null);filePathCallback=cb;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"text/csv","text/plain","application/csv","application/vnd.ms-excel","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"});try{startActivityForResult(i,FILE_CHOOSER_REQ);return true;}catch(Exception e){filePathCallback=null;return false;}}}
  private void openProductPicker(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"text/csv","text/plain","application/csv","application/vnd.ms-excel","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/octet-stream"});try{startActivityForResult(i,PRODUCT_IMPORT_REQ);}catch(Exception e){Toast.makeText(this,"Imeshindikana kufungua Storage/File Picker.",Toast.LENGTH_LONG).show();}}
  @Override protected void onActivityResult(int rc,int result,Intent data){super.onActivityResult(rc,result,data);if(rc==PRODUCT_IMPORT_REQ){if(result==RESULT_OK&&data!=null&&data.getData()!=null){try{Uri u=data.getData();byte[] bytes=readUri(u);String name=displayName(u),csv=(name.toLowerCase().endsWith(".xlsx")||isXlsx(bytes))?xlsxToCsv(bytes):new String(bytes,StandardCharsets.UTF_8);mainWebView.evaluateJavascript("window.receiveNativeImport("+jsQuote(csv)+","+jsQuote(name)+");",null);}catch(Exception e){mainWebView.evaluateJavascript("window.receiveNativeImportError("+jsQuote("Imeshindikana kusoma file: "+e.getMessage())+");",null);}}else mainWebView.evaluateJavascript("window.receiveNativeImportError('Import imeghairiwa.');",null);return;}if(rc==FILE_CHOOSER_REQ&&filePathCallback!=null){Uri[] r=null;if(result==RESULT_OK&&data!=null&&data.getData()!=null)r=new Uri[]{data.getData()};filePathCallback.onReceiveValue(r);filePathCallback=null;}}
  private byte[] readUri(Uri u)throws Exception{try(InputStream in=getContentResolver().openInputStream(u);ByteArrayOutputStream o=new ByteArrayOutputStream()){if(in==null)throw new Exception("Storage haikutoa file");byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)o.write(b,0,n);return o.toByteArray();}}
  private String displayName(Uri u){String r=null;android.database.Cursor c=getContentResolver().query(u,null,null,null,null);if(c!=null){try{int i=c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);if(i>=0&&c.moveToFirst())r=c.getString(i);}finally{c.close();}}return r==null?(u.getLastPathSegment()==null?"imported_file":u.getLastPathSegment()):r;}
  private boolean isXlsx(byte[] b){return b!=null&&b.length>3&&b[0]=='P'&&b[1]=='K'&&b[2]==3&&b[3]==4;}
  private HashMap<String,byte[]> unzip(byte[] b)throws Exception{HashMap<String,byte[]> m=new HashMap<>();ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(b));ZipEntry e;while((e=z.getNextEntry())!=null){ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] x=new byte[8192];int n;while((n=z.read(x))!=-1)o.write(x,0,n);m.put(e.getName(),o.toByteArray());z.closeEntry();}z.close();return m;}
  private Document xml(byte[] b)throws Exception{return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(b));}
  private String xlsxToCsv(byte[] bytes)throws Exception{HashMap<String,byte[]> z=unzip(bytes);ArrayList<String> ss=new ArrayList<>();byte[] sb=z.get("xl/sharedStrings.xml");if(sb!=null){Document d=xml(sb);NodeList si=d.getElementsByTagName("si");for(int i=0;i<si.getLength();i++){NodeList t=((Element)si.item(i)).getElementsByTagName("t");StringBuilder q=new StringBuilder();for(int j=0;j<t.getLength();j++)q.append(t.item(j).getTextContent());ss.add(q.toString());}}byte[] sheet=z.get("xl/worksheets/sheet1.xml");if(sheet==null)for(String k:z.keySet())if(k.startsWith("xl/worksheets/sheet")&&k.endsWith(".xml")){sheet=z.get(k);break;}if(sheet==null)throw new Exception("Excel sheet haijapatikana");Document d=xml(sheet);NodeList rows=d.getElementsByTagName("row");StringBuilder out=new StringBuilder();for(int r=0;r<rows.getLength();r++){Element row=(Element)rows.item(r);NodeList cells=row.getElementsByTagName("c");HashMap<Integer,String> vals=new HashMap<>();int max=-1;for(int j=0;j<cells.getLength();j++){Element c=(Element)cells.item(j);int col=colNumber(c.getAttribute("r"));if(col<0)continue;String type=c.getAttribute("t"),v="";if("inlineStr".equals(type)){NodeList t=c.getElementsByTagName("t");for(int k=0;k<t.getLength();k++)v+=t.item(k).getTextContent();}else{NodeList vs=c.getElementsByTagName("v");if(vs.getLength()>0)v=vs.item(0).getTextContent();if("s".equals(type))try{int ix=Integer.parseInt(v.trim());v=ix>=0&&ix<ss.size()?ss.get(ix):"";}catch(Exception ignored){}}vals.put(col,v);if(col>max)max=col;}for(int c=0;c<=max;c++){if(c>0)out.append(',');out.append(csvEscape(vals.getOrDefault(c,"")));}out.append('\n');}return out.toString();}
  private int colNumber(String r){int n=0,i=0;while(i<r.length()&&Character.isLetter(r.charAt(i))){n=n*26+(Character.toUpperCase(r.charAt(i))-'A'+1);i++;}return i==0?-1:n-1;}
  private String csvEscape(String s){return "\""+(s==null?"":s.replace("\"","\"\""))+"\"";}
  private String jsQuote(String s){if(s==null)s="";return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\r","\\r").replace("\n","\\n").replace("\u2028","\\u2028").replace("\u2029","\\u2029")+"\"";}
  private class AndroidBridge{
    @JavascriptInterface public void openProductImport(){runOnUiThread(MainActivity.this::openProductPicker);}
    @JavascriptInterface public void openFilePicker(){runOnUiThread(MainActivity.this::openProductPicker);}
    @JavascriptInterface public void shareWhatsApp(String msg,String phone){try{String p=(phone==null?"":phone).replaceAll("[^0-9]","");if(p.startsWith("0"))p="255"+p.substring(1);Uri u=Uri.parse("https://wa.me/"+p+"?text="+Uri.encode(msg==null?"":msg));Intent i=new Intent(Intent.ACTION_VIEW,u);i.setPackage("com.whatsapp");try{startActivity(i);}catch(Exception e){startActivity(new Intent(Intent.ACTION_VIEW,u));}}catch(Exception e){Toast.makeText(MainActivity.this,"Imeshindikana kufungua WhatsApp.",Toast.LENGTH_LONG).show();}}
    @JavascriptInterface public void savePdf(String html,String filename){runOnUiThread(()->{printWebView=new WebView(MainActivity.this);printWebView.getSettings().setJavaScriptEnabled(true);printWebView.setVisibility(View.INVISIBLE);printWebView.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String u){PrintManager pm=(PrintManager)getSystemService(PRINT_SERVICE);String job=(filename==null?"Finesty Sales Report":filename).replace(".pdf","");pm.print(job,v.createPrintDocumentAdapter(job),new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).setMinMargins(PrintAttributes.Margins.NO_MARGINS).build());}});printWebView.loadDataWithBaseURL(null,html==null?"":html,"text/html","UTF-8",null);});}
    @JavascriptInterface public void requestPermissions(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){MainActivity.this.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFICATION_REQ);return;}if(Build.VERSION.SDK_INT>=23&&Build.VERSION.SDK_INT<=32&&checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){MainActivity.this.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},STORAGE_REQ);return;}Toast.makeText(MainActivity.this,"Permissions ziko tayari. Import hutumia file unayochagua.",Toast.LENGTH_SHORT).show();}
    @JavascriptInterface public String getPermissionStatus(){String n=Build.VERSION.SDK_INT>=33?(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED?"Notifications: GRANTED":"Notifications: NOT GRANTED"):"Notifications: SYSTEM MANAGED";String s=Build.VERSION.SDK_INT<=32?(checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED?"Storage: GRANTED":"Storage: NOT GRANTED"):"Storage: File Picker per-file access";return n+" | "+s+" | PDF: Android Print/Save | WhatsApp: Open/Share";}
  }
}
