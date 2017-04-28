package org.bibledit.android;


import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.preference.PreferenceManager;
import android.content.SharedPreferences;
import java.io.FileOutputStream;
import android.content.Context;
import android.util.Log;
import java.io.OutputStreamWriter;
import android.content.res.AssetManager;
import java.io.IOException;
import java.lang.String;
import java.io.InputStream;
import java.io.File;
import java.io.OutputStream;
import android.os.Environment;
import android.content.Intent;
import android.view.View;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.NotificationManager;
import android.webkit.WebView;
import java.util.TimeZone;
import java.util.GregorianCalendar;
import java.util.Calendar;
import android.content.res.AssetManager;
import android.webkit.WebViewClient;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.os.Handler;
import android.net.Uri;
import java.util.Timer;
import java.util.TimerTask;
import android.os.Process;
import android.view.WindowManager;
import android.webkit.DownloadListener;
import android.app.DownloadManager;
import android.widget.Toast;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.graphics.Bitmap;
import android.widget.TabHost;
import android.widget.TabHost.TabContentFactory;
import android.widget.LinearLayout.LayoutParams;


// The activity's data is at /data/data/org.bibledit.android.
// It writes files to subfolder files.


public class MainActivity extends Activity
{
    
    WebView webview = null;
    TabHost tabhost = null;
    int resumecounter = 0;
    String webAppUrl = "http://localhost:8080";
    Timer timer;
    TimerTask timerTask;
    String previousSyncState;
    private ValueCallback<Uri> myUploadMessage;
    private final static int FILECHOOSER_RESULTCODE = 1;

    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == FILECHOOSER_RESULTCODE) {
            if (null == myUploadMessage) return;
            Uri result = intent == null || resultCode != RESULT_OK ? null : intent.getData();
            myUploadMessage.onReceiveValue (result);
            myUploadMessage = null;
        }
    }

    
    // Function is called when the app gets launched.
    @Override
    public void onCreate (Bundle savedInstanceState)
    {
        super.onCreate (savedInstanceState);
        // Log.d ("Bibledit", "onCreate");
        
        
        // The directory of the external files.
        // On a Nexus 10 this is /storage/emulated/0/Android/data/org.bibledit.android/files
        // Files in this directory cannot be made executable.
        // The system has a protection mechanism for this.
        String externalDirectory = getExternalFilesDir (null).getAbsolutePath ();
        
        // The protected directory that contains files that can be set executable.
        // This would be /data/data/org.bibledit.android/files
        // Files there can be set executable.
        String internalDirectory = getFilesDir ().getAbsolutePath ();
        
        // Take the external directory for the webroot, if it exists, else the internal directory.
        String webroot = externalDirectory;
        File file = new File (externalDirectory);
        if (!file.exists ()) webroot = internalDirectory;
        
        InitializeLibrary (webroot, webroot);
        
        SetTouchEnabled (true);
        
        StartLibrary ();
        
        StartWebView ();
        
        // Install the assets if needed.
        installAssets (webroot);
        
        // Log information about where to find Bibledit's data.
        Log ("Bibledit data location: " + webroot);
        
        // Keep-awake timer.
        startTimer ();

        /* FORCHROMEOS
        Intent browserIntent = new Intent (Intent.ACTION_VIEW, Uri.parse (webAppUrl));
        startActivity(browserIntent);
        FORCHROMEOS */
    }
    
    
    // A native method that is implemented by the native library which is packaged with this application.
    // There should be no understores (_) in the function name.
    // This avoids a "java.lang.UnsatisfiedLinkError: Native method not found" exception.
    public native String GetVersionNumber ();
    public native void SetTouchEnabled (Boolean enabled);
    public native void InitializeLibrary (String resources, String webroot);
    public native void StartLibrary ();
    public native Boolean IsRunning ();
    public native String IsSynchronizing ();
    public native String GetExternalUrl ();
    public native String GetPagesToOpen ();
    public native void StopLibrary ();
    public native void ShutdownLibrary ();
    public native void Log (String message);
    public native String GetLastPage ();
    
    
    @Override
    public boolean onCreateOptionsMenu (Menu menu)
    {
        return false;
    }
    
    
    // Function is called when the user starts the app.
    @Override
    protected void onStart ()
    {
        // Log.d ("Bibledit", "onStart");
        super.onStart();
        StartLibrary ();
        startTimer ();
    }
    
    
    // Function is called when the user returns to the activity.
    @Override
    protected void onRestart ()
    {
        // Log.d ("Bibledit", "onRestart");
        super.onRestart();
        StartLibrary ();
        startTimer ();
    }
    
    
    // Function is called when the app is moved to the foreground again.
    @Override
    public void onResume ()
    {
        // Log.d ("Bibledit", "onResume");
        super.onResume();
        StartLibrary ();
        checkUrl ();
        startTimer ();
    }
    
    
    // Function is called when the app is obscured.
    @Override
    public void onPause ()
    {
        // Log.d ("Bibledit", "onPause");
        super.onPause ();
        StopLibrary ();
        stopTimer ();
    }
    
    
    // Function is called when the user completely leaves the activity.
    @Override
    protected void onStop ()
    {
        // Log.d ("Bibledit", "onStop");
        super.onStop();
        StopLibrary ();
        stopTimer ();
    }
    
    
    // Function is called when the app gets completely destroyed.
    @Override
    public void onDestroy ()
    {
        // Log.d ("Bibledit", "onDestroy");
        super.onDestroy ();
        StopLibrary ();
        stopTimer ();
        // Crashes: while (IsRunning ()) {};
        ShutdownLibrary ();
    }
    
    
    // Function is called on device orientation and keyboard hide.
    // At least, it should be called. But it does not seem to happen.
    // Anyway the call is not needed because the webview reconfigures itself.
    // The app used to crash on device rotation.
    // The fix was adding
    // android:configChanges="orientation|keyboardHidden"
    // to the <activity> element in AndroidManifest.xml.
    // The app used to restart after a Bluetooth keyboard came on or went off.
    // This is according to the specifications.
    // But then the editor would go away, and the app would go back to the home screen after the restart.
    // The fix was to add "keyboard" to the above "configChanges" element.
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        // Log.d ("Bibledit", "onConfigurationChanged");
        super.onConfigurationChanged(newConfig);
    }
    
    
    // This is used to load the native Bibledit library on application startup.
    // Library libbibleditjni calls the Bibledit library.
    // The library has already been unpacked into
    // /data/data/org.bibledit.android/lib/libbbibleditjni.so
    // at installation time by the package manager.
    static {
        System.loadLibrary("gnustl_shared");
        System.loadLibrary("bibleditjni");
    }
    
    
    private void installAssets (final String webroot)
    {
        
        Thread thread = new Thread ()
        {
            @Override
            public void run ()
            {
                SharedPreferences preferences = getPreferences (Context.MODE_PRIVATE);
                String installedVersion = preferences.getString ("version", "");
                String libraryVersion = GetVersionNumber ();
                if (installedVersion.equals (libraryVersion)) return;
                
                try {
                    
                    // The assets are not visible in the standard filesystem, but remain inside the .apk file.
                    // The manager accesses them.
                    AssetManager assetManager = getAssets();
                    
                    // Read the asset index created by ant.
                    String [] files = null;
                    try {
                        InputStream input;
                        input = assetManager.open ("asset.external");
                        int size = input.available ();
                        byte [] buffer = new byte [size];
                        input.read (buffer);
                        input.close();
                        String text = new String (buffer);
                        files = text.split ("\\r?\\n");
                    } catch (IOException e) {
                        e.printStackTrace ();
                    }
                    
                    // Iterate through the asset files.
                    for (String filename : files) {
                        try {
                            // Read the file into memory.
                            InputStream input = assetManager.open ("external/" + filename);
                            int size = input.available ();
                            byte [] buffer = new byte [size];
                            input.read (buffer);
                            input.close ();
                            // Optionally create output directories.
                            File file = new File (filename);
                            String parent = file.getParent ();
                            if (parent != null) {
                                File parentFile = new File (webroot, parent);
                                if (!parentFile.exists ()) {
                                    parentFile.mkdirs ();
                                }
                            }
                            file = null;
                            // Write the file to the external webroot directory.
                            File outFile = new File (webroot, filename);
                            OutputStream out = new FileOutputStream (outFile);
                            out.write (buffer, 0, size);
                            out.flush ();
                            out.close ();
                            outFile = null;
                            out = null;
                            //Log.i (filename, webroot);
                        } catch(IOException e) {
                            e.printStackTrace ();
                        }
                    }
                    
                }
                catch (Exception e) {
                    e.printStackTrace ();
                }
                finally {
                }
                preferences.edit ().putString ("version", GetVersionNumber ()).apply ();
            }
        };
        thread.start ();
    }
    
    
    // Checks whether the browser has a Bibledit page opened.
    // If not, it navigates the browser to the Bibledit home page.
    private void checkUrl ()
    {
        // Bail out on the activity's first resume.
        // Else it crashes on checking its URL.
        resumecounter++;
        if (resumecounter <= 1) return;
        
        Boolean load_index = false;
        String url = webview.getUrl ();
        if (url.length () >= 21) {
            String bit = url.substring (0, 21);
            if (bit.compareTo (webAppUrl) != 0) {
                load_index = true;
            }
        } else load_index = true;
        if (load_index) {
            // Load the index page.
            webview.loadUrl (webAppUrl);
        } else {
            // Just to be sure that any javascript runs, reload the loaded URL.
            // This was disabled later, as reloading the page could lead to the loss of the information that page contained, e.g. when creating a note.
            // webview.loadUrl (url);
        }
    }
    
    
    /*
     
     There was an idea that the app would shut down itself after it would be in the background for a while.
     This works well when another app is started and thus Bibledit goes to the background.
     But when the screen is powered off, then when Bibledit quits itself, Android keeps restarting it.
     And when the screen is powered on again, then Bibledit cannot find the page.
     Thus since this does not work well, it was not implemented.
     
     System.runFinalizersOnExit (true);
     this.finish ();
     Process.killProcess (Process.myPid());
     System.exit (0);
     
     */
    
    
    private void startTimer ()
    {
        stopTimer ();
        timer = new Timer();
        initializeTimerTask();
        timer.schedule (timerTask, 1000);
    }
    
    
    private void stopTimer ()
    {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }
    
    private void initializeTimerTask() {
        timerTask = new TimerTask() {
            public void run() {
                
                // Check whether to keep the screen on during send and receive.
                String syncState = IsSynchronizing ();
                // Log.d ("Bibledit syncing", syncState);
                if (syncState.equals ("true")) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        }
                    });
                    // Log.d ("Bibledit", "keep screen on");
                }
                if (syncState.equals ("false")) {
                    if (syncState.equals (previousSyncState)) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                            }
                        });
                        // Log.d ("Bibledit", "do not keep screen on");
                    }
                }
                previousSyncState = syncState;
                
                // Check whether to open an external URL in the system browser.
                String externalUrl = GetExternalUrl ();
                if (externalUrl != null && !externalUrl.isEmpty ()) {
                    Log.d ("Bibledit start Browser", externalUrl);
                    Intent browserIntent = new Intent (Intent.ACTION_VIEW, Uri.parse (externalUrl));
                    startActivity(browserIntent);
                }
                
                // Checking on whether to open tabbed views.
                String URLs = GetPagesToOpen ();
                Log.d ("Bibledit pages to open", URLs);
                if (URLs != null && !URLs.isEmpty()) {
                    String lines[] = URLs.split ("\\n");
                    Log.d ("Lines count ", String.valueOf (lines.length));
                    if (lines.length == 1) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                StartWebView ();
                            }
                        });
                    } else {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                StartTabHost ();
                            }
                        });
                    }
                }

                // Start timeout for next iteration.
                startTimer ();
            }
        };
    }
    
    
    @Override
    public void onBackPressed() {
        // The Android back button navigates back in the web view.
        // That is the behaviour people expect.
        if (webview.canGoBack()) {
            webview.goBack();
            return;
        }
        
        // Otherwise defer to system default behavior.
        super.onBackPressed();
    }
    
    
    private void StartWebView ()
    {
        if (webview != null) return;
        tabhost = null;
        webview = new WebView (this);
        setContentView (webview);
        webview.getSettings().setJavaScriptEnabled (true);
        webview.getSettings().setBuiltInZoomControls (true);
        webview.getSettings().setSupportZoom (true);
        webview.setWebViewClient(new WebViewClient());
        webview.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart (String url, String userAgent, String contentDisposition, String mimetype,
                                         long contentLength) {
                DownloadManager.Request request = new DownloadManager.Request (Uri.parse (url));
                request.allowScanningByMediaScanner();
                // Notification once download is completed.
                request.setNotificationVisibility (DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                Uri uri = Uri.parse (url);
                String filename = uri.getLastPathSegment ();
                request.setDestinationInExternalPublicDir (Environment.DIRECTORY_DOWNLOADS, filename);
                DownloadManager dm = (DownloadManager) getSystemService (DOWNLOAD_SERVICE);
                dm.enqueue (request);
                // Notification that the file is being downloaded.
                Toast.makeText (getApplicationContext(), "Downloading file", Toast.LENGTH_LONG).show ();
            }
        });
        webview.setWebChromeClient(new WebChromeClient() {
            // The undocumented method overrides.
            // The compiler fails if you try to put @Override here.
            // It needs three interfaces to handle the various versions of Android.
            public void openFileChooser(ValueCallback<Uri> uploadMsg) {
                myUploadMessage = uploadMsg;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                MainActivity.this.startActivityForResult(Intent.createChooser(intent, "File Chooser"), FILECHOOSER_RESULTCODE);
            }
            public void openFileChooser( ValueCallback uploadMsg, String acceptType) {
                myUploadMessage = uploadMsg;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                MainActivity.this.startActivityForResult(Intent.createChooser(intent, "File Browser"), FILECHOOSER_RESULTCODE);
            }
            public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType, String capture) {
                myUploadMessage = uploadMsg;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                MainActivity.this.startActivityForResult (Intent.createChooser (intent, "File Chooser"), MainActivity.FILECHOOSER_RESULTCODE);
            }
        });
        webview.loadUrl (webAppUrl);
    }
    
    
    private void StartTabHost ()
    {
        if (tabhost != null) return;

        webview = null;
        
        setContentView (R.layout.main);
        
        tabhost = (TabHost) findViewById (R.id.tabhost);
        tabhost.setup ();
        
        TabHost.TabSpec tabspec;
        TabContentFactory factory;
        
        tabspec = tabhost.newTabSpec("T");
        tabspec.setIndicator("Translate");
        factory = new TabHost.TabContentFactory () {
            @Override
            public View createTabContent (String tag) {
                WebView webview = new WebView (getApplicationContext ());
                webview.getSettings().setJavaScriptEnabled (true);
                webview.setWebViewClient (new WebViewClient());
                webview.loadUrl ("https://bibledit.org:8081/editone/index");
                return webview;
            }
        };
        tabspec.setContent(factory);
        tabhost.addTab (tabspec);
        
        tabspec = tabhost.newTabSpec("R");
        tabspec.setIndicator("Resources");
        factory = new TabHost.TabContentFactory () {
            @Override
            public View createTabContent (String tag) {
                WebView webview = new WebView (getApplicationContext ());
                webview.getSettings().setJavaScriptEnabled (true);
                webview.setWebViewClient (new WebViewClient());
                webview.loadUrl ("https://bibledit.org:8081/resource/index");
                return webview;
            }
        };
        tabspec.setContent(factory);
        tabhost.addTab (tabspec);
        
        tabspec = tabhost.newTabSpec("N");
        tabspec.setIndicator("Notes");
        factory = new TabHost.TabContentFactory () {
            @Override
            public View createTabContent (String tag) {
                WebView webview = new WebView (getApplicationContext ());
                webview.getSettings().setJavaScriptEnabled (true);
                webview.setWebViewClient (new WebViewClient());
                webview.loadUrl ("https://bibledit.org:8081/notes/index");
                return webview;
            }
        };
        tabspec.setContent(factory);
        tabhost.addTab (tabspec);
        
        for (int i = 0; i < tabhost.getTabWidget().getChildCount(); i++) {
            tabhost.getTabWidget().getChildAt(i).getLayoutParams().height /= 2;
        }
        
    }

}
