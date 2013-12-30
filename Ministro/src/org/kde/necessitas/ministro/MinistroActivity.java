/*
    Copyright (c) 2011-2014, BogDan Vatra <bogdan@kde.org>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.kde.necessitas.ministro;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.concurrent.Semaphore;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.http.client.ClientProtocolException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.StatFs;
import android.provider.Settings;
import android.util.Log;

@SuppressLint("Wakelock")
public class MinistroActivity extends Activity
{
    // 20 seconds for connection timeout
    public static final int CONNECTION_TIMEOUT = 20000;

    // 10 seconds for read timeout
    public static final int READ_TIMEOUT = 10000;

    public native static int nativeChmode(String filepath, int mode);

    private int m_id = -1;
    private Session m_session = null;
    private String m_rootPath = null;
    private WakeLock m_wakeLock = null;

    private void checkNetworkAndDownload(final boolean update, boolean checkOnline)
    {
        if (!checkOnline || isOnline(this))
            new CheckLibraries().execute(update);
        else
        {
            AlertDialog.Builder builder = new AlertDialog.Builder(MinistroActivity.this);
            builder.setMessage(getResources().getString(R.string.ministro_network_access_msg));
            builder.setCancelable(true);
            builder.setNeutralButton(getResources().getString(R.string.settings_msg), new DialogInterface.OnClickListener()
            {
                public void onClick(DialogInterface dialog, int id)
                {
                    final ProgressDialog m_dialog = ProgressDialog.show(MinistroActivity.this, null, getResources().getString(R.string.wait_for_network_connection_msg), true, true,
                            new DialogInterface.OnCancelListener()
                            {
                                public void onCancel(DialogInterface dialog)
                                {
                                    finishMe(Session.Result.Canceled);
                                }
                            });
                    getApplication().registerReceiver(new BroadcastReceiver()
                    {
                        @Override
                        public void onReceive(Context context, Intent intent)
                        {
                            if (isOnline(MinistroActivity.this))
                            {
                                try
                                {
                                    getApplication().unregisterReceiver(this);
                                }
                                catch (Exception e)
                                {
                                    e.printStackTrace();
                                }
                                runOnUiThread(new Runnable()
                                {
                                    public void run()
                                    {
                                        m_dialog.dismiss();
                                        new CheckLibraries().execute(update);
                                    }
                                });
                            }
                        }
                    }, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
                    try
                    {
                        startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                        try
                        {
                            startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                        }
                        catch (Exception e1)
                        {

                            e1.printStackTrace();
                        }
                    }
                    dialog.dismiss();
                }
            });
            builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener()
            {
                public void onClick(DialogInterface dialog, int id)
                {
                    dialog.cancel();
                }
            });
            builder.setOnCancelListener(new DialogInterface.OnCancelListener()
            {
                public void onCancel(DialogInterface dialog)
                {
                    finishMe(Session.Result.Canceled);
                }
            });
            AlertDialog alert = builder.create();
            alert.show();
        }
    }

    private AlertDialog m_distSpaceDialog = null;
    private final int freeSpaceCode = 0xf3ee500;
    private Semaphore m_diskSpaceSemaphore = new Semaphore(0);

    @SuppressLint("InlinedApi")
    private boolean checkFreeSpace(final long size) throws InterruptedException
    {
        final StatFs stat = new StatFs(m_rootPath);
        if (stat.getAvailableBlocks() < (size/stat.getBlockSize() + 1))
        {
            runOnUiThread(new Runnable()
            {
                public void run()
                {

                    AlertDialog.Builder builder = new AlertDialog.Builder(MinistroActivity.this);
                    builder.setMessage(getResources().getString(R.string.ministro_disk_space_msg, (size/stat.getBlockSize() - stat.getAvailableBlocks()) * stat.getBlockSize() / 1024 + "Kb"));
                    builder.setCancelable(true);
                    builder.setNeutralButton(getResources().getString(R.string.settings_msg), new DialogInterface.OnClickListener()
                    {
                        public void onClick(DialogInterface dialog, int id)
                        {
                            try
                            {
                                startActivityForResult(new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS), freeSpaceCode);
                            }
                            catch (Exception e)
                            {
                                e.printStackTrace();
                                try
                                {
                                    startActivityForResult(new Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS), freeSpaceCode);
                                }
                                catch (Exception e1)
                                {

                                    e1.printStackTrace();
                                }
                            }
                        }
                    });
                    builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener()
                    {
                        public void onClick(DialogInterface dialog, int id)
                        {
                            dialog.dismiss();
                            m_diskSpaceSemaphore.release();
                        }
                    });
                    builder.setOnCancelListener(new DialogInterface.OnCancelListener()
                    {
                        public void onCancel(DialogInterface dialog)
                        {
                            dialog.dismiss();
                            m_diskSpaceSemaphore.release();
                        }
                    });
                    m_distSpaceDialog = builder.create();
                    m_distSpaceDialog.show();
                }
            });
            m_diskSpaceSemaphore.acquire();
        }
        else
            return true;

        return stat.getAvailableBlocks() > (size/stat.getBlockSize() + 1);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == freeSpaceCode)
        {
            m_diskSpaceSemaphore.release();
            try
            {
                if (m_distSpaceDialog != null)
                {
                    m_distSpaceDialog.dismiss();
                    m_distSpaceDialog = null;
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    private ServiceConnection m_ministroConnection = new ServiceConnection()
    {
        public void onServiceConnected(ComponentName name, IBinder service)
        {
            if (getIntent().hasExtra("id"))
            {
                m_id = getIntent().getExtras().getInt("id");
                m_session = MinistroService.instance().getSession(m_id);
                if (m_session != null)
                {
                    if (m_session.onlyExtractStyleAndSsl())
                    {
                        checkNetworkAndDownload(false, false);
                    }
                    else
                    {
                        AlertDialog.Builder builder = new AlertDialog.Builder(MinistroActivity.this);
                        builder.setMessage(getResources().getString(R.string.download_app_libs_msg, m_session.getApplicationName())).setCancelable(false)
                                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener()
                                {
                                    public void onClick(DialogInterface dialog, int id)
                                    {
                                        dialog.dismiss();
                                        checkNetworkAndDownload(false, true);
                                    }
                                }).setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener()
                                {
                                    public void onClick(DialogInterface dialog, int id)
                                    {
                                        dialog.cancel();
                                        finishMe(Session.Result.Canceled);
                                    }
                                });
                        AlertDialog alert = builder.create();
                        try
                        {
                            alert.show();
                        }
                        catch (Exception e)
                        {
                            e.printStackTrace();
                            checkNetworkAndDownload(false, true);
                        }
                    }
                }
                else
                {
                    m_id = -1;
                    finishMe(Session.Result.Canceled);
                }
            }
            else
            {
                m_id = -1;
                m_session = MinistroService.instance().getUpdateSession();
                if (m_session != null)
                    checkNetworkAndDownload(true, true);
                else
                    finish();
            }
        }

        public void onServiceDisconnected(ComponentName name)
        {
            m_ministroConnection = null;
        }
    };

    void finishMe(Session.Result res)
    {
        if (-1 != m_id && null != MinistroService.instance())
            MinistroService.instance().retrievalFinished(m_id, res);
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancelAll();
        finish();
    }

    public static boolean isOnline(Context c)
    {
        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo != null && netInfo.isConnectedOrConnecting())
            return true;
        return false;
    }

    private static String deviceSupportedFeatures(String supportedFeatures)
    {
        if (null == supportedFeatures)
            return "";
        String[] serverFeaturesList = supportedFeatures.trim().split(" ");
        String[] deviceFeaturesList = null;
        try
        {
            FileInputStream fstream = new FileInputStream("/proc/cpuinfo");
            DataInputStream in = new DataInputStream(fstream);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String strLine;
            while ((strLine = br.readLine()) != null)
            {
                if (strLine.startsWith("Features"))
                {
                    deviceFeaturesList = strLine.substring(strLine.indexOf(":") + 1).trim().split(" ");
                    break;
                }
            }
            br.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return "";
        }

        String features = "";
        for (String sfeature : serverFeaturesList)
            for (String dfeature : deviceFeaturesList)
                if (sfeature.equals(dfeature))
                    features += "_" + dfeature;

        return features;
    }

    public double downloadVersionXmlFile(Integer sourceId, boolean checkOnly)
    {
        if (!isOnline(this))
            return -1;
        try
        {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document dom = null;
            Element root = null;
            URLConnection connection = m_session.getVersionsFileUrl(sourceId).openConnection();
            connection.setConnectTimeout(CONNECTION_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            dom = builder.parse(connection.getInputStream());
            root = dom.getDocumentElement();
            root.normalize();
            double version = Double.valueOf(root.getAttribute("latest"));
            double sver = m_session.getVersion(sourceId);
            if (sver >= version)
                return sver;

            if (checkOnly)
                return version;
            String supportedFeatures = null;
            if (root.hasAttribute("features"))
                supportedFeatures = root.getAttribute("features");
            connection = m_session.getLibsXmlUrl(sourceId, version + deviceSupportedFeatures(supportedFeatures)).openConnection();
            String xmlFilePath = MinistroService.instance().getVersionXmlFile(sourceId, m_session.getRepository());
            new File(xmlFilePath).delete();
            FileOutputStream outstream = new FileOutputStream(xmlFilePath);
            InputStream instream = connection.getInputStream();
            byte[] tmp = new byte[2048];
            int downloaded;
            while ((downloaded = instream.read(tmp)) != -1)
                outstream.write(tmp, 0, downloaded);

            outstream.close();
            MinistroService.instance().createSourcePath(sourceId, m_session.getRepository());
            return version;
        }
        catch (ClientProtocolException e)
        {
            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        catch (ParserConfigurationException e)
        {
            e.printStackTrace();
        }
        catch (IllegalStateException e)
        {
            e.printStackTrace();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return -1;
    }

    private class DownloadManager extends AsyncTask<Library, Integer, Long>
    {
        private ProgressDialog m_dialog = null;
        private String m_status = getResources().getString(R.string.start_downloading_msg);
        private int m_totalSize = 0, m_totalProgressSize = 0;

        @Override
        protected void onPreExecute()
        {
            m_dialog = new ProgressDialog(MinistroActivity.this);
            m_dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            m_dialog.setTitle(getResources().getString(R.string.downloading_qt_libraries_msg));
            m_dialog.setMessage(m_status);
            m_dialog.setCancelable(true);
            m_dialog.setCanceledOnTouchOutside(false);
            m_dialog.setOnCancelListener(new DialogInterface.OnCancelListener()
            {
                public void onCancel(DialogInterface dialog)
                {
                    DownloadManager.this.cancel(false);
                }
            });
            try
            {
                m_dialog.show();
            }
            catch (Exception e)
            {
                e.printStackTrace();
                m_dialog = null;
            }
            super.onPreExecute();
        }

        private boolean DownloadItem(String url, String rootPath, String file, long size, String fileSha1) throws NoSuchAlgorithmException, MalformedURLException, IOException
        {
            for (int i = 0; i < 2; i++)
            {
                MessageDigest digester = MessageDigest.getInstance("SHA-1");
                URLConnection connection = new URL(url).openConnection();
                Library.mkdirParents(rootPath, file, 1);
                String filePath = rootPath + file;
                int progressSize = 0;
                try
                {
                    FileOutputStream outstream = new FileOutputStream(filePath);
                    InputStream instream = connection.getInputStream();
                    int downloaded;
                    byte[] tmp = new byte[2048];
                    int oldProgress = -1;
                    while ((downloaded = instream.read(tmp)) != -1)
                    {
                        if (isCancelled())
                            break;
                        progressSize += downloaded;
                        m_totalProgressSize += downloaded;
                        digester.update(tmp, 0, downloaded);
                        outstream.write(tmp, 0, downloaded);
                        int progress = (int) (progressSize * 100 / size);
                        if (progress != oldProgress)
                        {
                            publishProgress(progress, m_totalProgressSize);
                            oldProgress = progress;
                        }
                    }
                    String sha1 = Library.convertToHex(digester.digest());
                    if (sha1.equalsIgnoreCase(fileSha1))
                    {
                        outstream.close();
                        nativeChmode(filePath, 0644);
                        return true;
                    }
                    else
                        Log.e("Ministro", "sha1 mismatch, the file:" + file + " will be removed, expected sha1:" + fileSha1 + " got sha1:" + sha1 + " file was downloaded from " + url);
                    outstream.close();
                    File f = new File(filePath);
                    f.delete();
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                    File f = new File(filePath);
                    f.delete();
                }
                m_totalProgressSize -= progressSize;
            }
            return false;
        }

        @Override
        protected Long doInBackground(Library... params)
        {
            try
            {
                for (int i = 0; i < params.length; i++)
                {
                    m_totalSize += params[i].size;
                    if (null != params[i].needs)
                        for (int j = 0; j < params[i].needs.length; j++)
                            m_totalSize += params[i].needs[j].size;
                }
                m_dialog.setMax(m_totalSize);
                if (!checkFreeSpace(m_totalSize))
                    return null;

                for (int i = 0; i < params.length; i++)
                {
                    if (isCancelled())
                        break;

                    synchronized (m_status)
                    {
                        m_status = params[i].name + " ";
                    }
                    publishProgress(0, m_totalProgressSize);
                    String rootPath = MinistroService.instance().getLibsRootPath(params[i].sourceId, m_session.getRepository());
                    if (!DownloadItem(params[i].url, rootPath, params[i].filePath, params[i].size, params[i].sha1))
                        break;

                    if (null != params[i].needs)
                        for (int j = 0; j < params[i].needs.length; j++)
                        {
                            synchronized (m_status)
                            {
                                m_status = params[i].needs[j].name + " ";
                            }
                            publishProgress(0, m_totalProgressSize);
                            if (!DownloadItem(params[i].needs[j].url, rootPath, params[i].needs[j].filePath, params[i].needs[j].size, params[i].needs[j].sha1))
                            {
                                for (int k = 0; k < j; k++)
                                    // remove previous needed files
                                    new File(rootPath + params[i].needs[k].filePath).delete();
                                // remove the parent
                                new File(rootPath + params[i].filePath).delete();
                                break;
                            }
                        }
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }

            if (isCancelled())
                finishMe(Session.Result.Canceled);

            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values)
        {
            try
            {
                if (m_dialog != null)
                {
                    synchronized (m_status)
                    {
                        m_dialog.setMessage(m_status + values[0] + "%");
                        m_dialog.setProgress(values[1]);
                    }
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            super.onProgressUpdate(values);
        }

        @Override
        protected void onPostExecute(Long result)
        {
            super.onPostExecute(result);
            if (m_dialog != null)
            {
                m_dialog.dismiss();
                m_dialog = null;
            }
            finishMe(Session.Result.Completed);
        }
    }

    private class CheckLibraries extends AsyncTask<Boolean, String, Boolean>
    {
        private ProgressDialog m_dialog = null;
        private final HashMap<String, Library> newLibs = new HashMap<String, Library>();
        private String m_message;

        @Override
        protected void onPreExecute()
        {
            try
            {
                m_dialog = ProgressDialog.show(MinistroActivity.this, null, getResources().getString(R.string.checking_libraries_msg), true, true);
            }
            catch (Exception e)
            {
                e.printStackTrace();
                m_dialog = null;
            }
            super.onPreExecute();
        }

        @Override
        protected Boolean doInBackground(Boolean... update)
        {
            try
            {
                SharedPreferences preferences = m_session.getPreferences();
                // extract device look&feel
                String _style = "style/" + m_session.getDisplayDPI();
                if (m_session.extractStyle())
                {
                    m_message = getResources().getString(R.string.extracting_look_n_feel_msg);
                    publishProgress(m_message);
                    String path = Library.mkdirParents(m_rootPath, _style, 0);
                    if (!(new File(path + "/style.json").exists()))
                    {
                        // Extract default (dark) theme
                        setTheme(android.R.style.Theme);
                        new ExtractStyle(MinistroActivity.this, path);
                    }
                    String stylePath = path;
                    String[] neededThemes = m_session.getThemes();
                    if (neededThemes != null) {
                        for (String theme: neededThemes) {
                            try {
                                path = Library.mkdirParents(stylePath, theme, 0);
                                if (!(new File(path + "/style.json").exists())) {
                                    setTheme(android.R.style.class.getDeclaredField(theme).getInt(null));
                                    new ExtractStyle(MinistroActivity.this, path);
                                }
                            } catch(Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    setTheme(android.R.style.Theme);

                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putString(Session.MINISTRO_VERSION, getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
                    editor.commit();
                }

                // extract device root certificates
                if (!(new File(m_session.getMinistroSslRootPath()).exists()))
                {
                    m_message = getResources().getString(R.string.extracting_SSL_msg);
                    publishProgress(m_message);
                    String path = Library.mkdirParents(m_rootPath, "ssl", 0);
                    Library.removeAllFiles(path, true);
                    try
                    {
                        KeyStore ks = null;
                        if (Build.VERSION.SDK_INT > 13)
                        {
                            ks = KeyStore.getInstance("AndroidCAStore");
                            ks.load(null, null);
                        }
                        else
                        {
                            ks = KeyStore.getInstance(KeyStore.getDefaultType());
                            String cacertsPath = System.getProperty("javax.net.ssl.trustStore");
                            if (null == cacertsPath)
                                cacertsPath = "/system/etc/security/cacerts.bks";
                            FileInputStream instream = new FileInputStream(new File(cacertsPath));
                            ks.load(instream, null);
                        }

                        for (Enumeration<String> aliases = ks.aliases(); aliases.hasMoreElements();)
                        {
                            String aName = aliases.nextElement();
                            try
                            {
                                X509Certificate cert = (X509Certificate) ks.getCertificate(aName);
                                if (null == cert)
                                    continue;
                                String filePath = path + "/" + cert.getType() + "_" + cert.hashCode() + ".der";
                                FileOutputStream outstream = new FileOutputStream(new File(filePath));
                                byte buff[] = cert.getEncoded();
                                outstream.write(buff, 0, buff.length);
                                outstream.close();
                                nativeChmode(filePath, 0644);
                            }
                            catch (KeyStoreException e)
                            {
                                e.printStackTrace();
                            }
                            catch (Exception e)
                            {
                                e.printStackTrace();
                            }
                        }
                    }
                    catch (KeyStoreException e)
                    {
                        e.printStackTrace();
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                    catch (NoSuchAlgorithmException e)
                    {
                        e.printStackTrace();
                    }
                    catch (CertificateException e)
                    {
                        e.printStackTrace();
                    }
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putString("CODENAME", android.os.Build.VERSION.CODENAME);
                    editor.putString("INCREMENTAL", android.os.Build.VERSION.INCREMENTAL);
                    editor.putString("RELEASE", android.os.Build.VERSION.RELEASE);
                    editor.commit();
                }

                if (m_session.onlyExtractStyleAndSsl())
                    return false;

                boolean refreshLibraries = false;
                for (Integer sourceId : m_session.getSourcesIds())
                {
                    // if is an update command or the xml file doesn't exists
                    if ((update[0] || m_session.getVersion(sourceId) < 0) && downloadVersionXmlFile(sourceId, false) > -1)
                    {
                        // get the old libraries
                        HashMap<String, Library> oldLibs = m_session.getChangedLibraries(sourceId);
                        if (oldLibs != null)
                            newLibs.putAll(oldLibs);
                        refreshLibraries = true;
                        synchronized (SourcesCache.sync)
                        {
                            SourcesCache.s_sourcesCache.remove(sourceId);
                        }
                    }
                }

                if (refreshLibraries)
                    m_session.refreshLibraries(false);

                if (!update[0])
                    m_session.checkModules(newLibs);
                return true;
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            return false;
        }

        @Override
        protected void onProgressUpdate(String... messages)
        {
            try
            {
                if (null != m_dialog)
                    m_dialog.setMessage(messages[0]);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            super.onProgressUpdate(messages);
        }

        @Override
        protected void onPostExecute(Boolean result)
        {
            try
            {
                if (null != m_dialog)
                {
                    m_dialog.dismiss();
                    m_dialog = null;
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            if (newLibs.size() > 0 && result)
            {
                Library[] libs = new Library[newLibs.size()];
                libs = newLibs.values().toArray(libs);
                new DownloadManager().execute(libs);
            }
            else
                finishMe(Session.Result.Completed);
            super.onPostExecute(result);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        m_rootPath = getFilesDir().getAbsolutePath() + "/dl/";
        File dir = new File(m_rootPath);
        dir.mkdirs();
        nativeChmode(m_rootPath, 0755);
        bindService(new Intent("org.kde.necessitas.ministro.IMinistro"), m_ministroConnection, Context.BIND_AUTO_CREATE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        unbindService(m_ministroConnection);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        // Avoid activity from being destroyed/created
        super.onConfigurationChanged(newConfig);
    }

    static
    {
        System.loadLibrary("ministro");
    }
}
