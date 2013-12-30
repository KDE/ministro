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

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.util.Log;
import android.util.SparseArray;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ListIterator;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class Ministro extends Application {
    public static final String TAG = "MinistroService";

    private static final String MINISTRO_CHECK_UPDATES_KEY = "LASTCHECK";
    private static final String MINISTRO_CHECK_FREQUENCY_KEY = "CHECKFREQUENCY";
    private static final String MINISTRO_REPOSITORY_KEY = "REPOSITORY";
    private static final String MINISTRO_MIGRATED_KEY = "MIGRATED";
    private static final String MINISTRO_DEFAULT_REPOSITORY = "stable";
    private static final String MINISTRO_SOURCES_KEY = "SOURCES";
    private static final String MINISTRO_CHECK_CRC_KEY = "CHECK_CRC";

    private HashMap<String, Integer> m_sources = new HashMap<String, Integer>();
    private String m_repository = null;
    private long m_lastCheckUpdates = 0;
    private long m_checkFrequency = 7l * 24 * 3600 * 1000; // 7 days
    private int m_nextId = 0;
    private String m_ministroRootPath = null;

    SparseArray<SourcesCache> m_sourcesCache = new SparseArray<SourcesCache>();

    public SparseArray<HashMap<String, HashSet<String>>> m_sourceUsers = new SparseArray<HashMap<String, HashSet<String>>>();

    JSONArray saveUsers(int sourceId) throws JSONException
    {
        HashMap<String, HashSet<String>> source = m_sourceUsers.get(sourceId);
        if (source == null)
            return null;

        JSONArray ret = new JSONArray();
        for (String user : source.keySet())
        {
            JSONObject userObject = new JSONObject();
            userObject.put("user", user);
            JSONArray modules = new JSONArray();
            for (String module: source.get(user))
                modules.put(module);
            userObject.put("modules", modules);
            ret.put(userObject);
        }
        return ret;
    }

    void loadUsers(HashSet<String> installedPackages, int sourceId, JSONArray users) throws JSONException
    {
        if (users == null)
            return;

        HashMap<String, HashSet<String>> source = new HashMap<String, HashSet<String>>();
        for (int i = 0; i < users.length(); i++)
        {
            JSONObject userObject = users.getJSONObject(i);
            String user = userObject.getString("user");
            if (!installedPackages.contains(user))
                continue;

            HashSet<String> modules = new HashSet<String>();
            JSONArray moduleArray = userObject.getJSONArray("modules");
            for (int m = 0; m < moduleArray.length(); m++)
                modules.add(moduleArray.getString(m));
            source.put(user, modules);
        }
        m_sourceUsers.put(sourceId, source);
    }

    private void allModules(SourcesCache sc, String module, HashSet<String> ret)
    {
        if (!sc.downloadedLibraries.containsKey(module))
            return;

        ret.add(module);
        String[] depends = sc.downloadedLibraries.get(module).depends;
        if (depends != null)
            for (String depend : depends)
                if (!ret.contains(depend))
                    allModules(sc, depend, ret);
    }

    void updateSourcesUsers(String[] packageNames, ArrayList<Integer> sourceIds, String[] modules)
    {
        HashSet<String> requiredModules = new HashSet<String>(Arrays.asList(modules));
        ListIterator<Integer> sit = sourceIds.listIterator(sourceIds.size());
        while(sit.hasPrevious())
        {
            int sourceId = sit.previous();
            SourcesCache sc = m_sourcesCache.get(sourceId);
            if (null == sc)
                continue;

            HashSet<String> reqModules = new HashSet<String>();
            for (String module : requiredModules)
                allModules(sc, module, reqModules);

            requiredModules.removeAll(reqModules);
            HashMap<String, HashSet<String>> source = m_sourceUsers.get(sourceId);
            if (source == null)
                source = new HashMap<String, HashSet<String>>();

            for (String user : packageNames)
                source.put(user, reqModules);

            m_sourceUsers.put(sourceId, source);
        }
    }

    private boolean putLibraries(LibrariesStruct libs, int sourceId)
    {
        SourcesCache sc = m_sourcesCache.get(sourceId);
        if (sc == null)
            return false;

        libs.sourcesCache.put(sourceId, sc);

        libs.downloadedLibraries.putAll(sc.downloadedLibraries);
        libs.availableLibraries.putAll(sc.availableLibraries);

        if (sc.qtVersion > libs.qtVersion)
            libs.qtVersion = sc.qtVersion;

        if (sc.loaderClassName != null)
            libs.loaderClassName = sc.loaderClassName;

        libs.applicationParams.addAll(sc.applicationParams);
        libs.environmentVariables.putAll(sc.environmentVariables);

        return true;
    }

    // this method reload all downloaded libraries
    public LibrariesStruct refreshLibraries(ArrayList<Integer> sourcesIds, int displayDPI, boolean checkCrc)
    {
        LibrariesStruct ret = new LibrariesStruct();

        synchronized (this)
        {
            try
            {
                for (Integer sourceId : sourcesIds)
                {
                    if (putLibraries(ret, sourceId))
                        continue;

                    File file = new File(getVersionXmlFile(sourceId, getRepository()));
                    if (!file.exists())
                        continue;

                    SourcesCache sc = new SourcesCache();
                    DocumentBuilderFactory documentFactory = DocumentBuilderFactory.newInstance();
                    DocumentBuilder documentBuilder = documentFactory.newDocumentBuilder();
                    Document dom = documentBuilder.parse(new FileInputStream(file));
                    Element root = dom.getDocumentElement();
                    sc.version = Double.valueOf(root.getAttribute("version"));
                    sc.loaderClassName = root.getAttribute("loaderClassName");
                    if (root.hasAttribute("applicationParameters"))
                    {
                        String params = root.getAttribute("applicationParameters");
                        if (params != null)
                        {
                            params = params.replaceAll("MINISTRO_PATH", getFilesDir().getAbsolutePath());
                            ArrayList<String> ap = new ArrayList<String>();
                            for (String parameter : params.split("\t"))
                                if (parameter.length() > 0)
                                    ap.add(parameter);
                            if (ap.size() > 0)
                                sc.applicationParams = ap;
                        }
                    }

                    if (root.hasAttribute("environmentVariables"))
                    {
                        String environmentVariables = root.getAttribute("environmentVariables");
                        if (environmentVariables != null)
                        {
                            environmentVariables = environmentVariables.replaceAll("MINISTRO_PATH", getMinistroRootPath());
                            environmentVariables = environmentVariables.replaceAll("MINISTRO_SOURCE_ROOT_PATH", getLibsRootPath(sourceId, getRepository()));
                            HashMap<String, String> envVars = new HashMap<String, String>();
                            for (String envPair : environmentVariables.split("\t"))
                            {
                                int pos = envPair.indexOf('=');
                                if (pos > 0 && pos + 1 < envPair.length())
                                    envVars.put(envPair.substring(0, pos), envPair.substring(pos + 1));
                            }
                            if (envVars.size() > 0)
                                sc.environmentVariables = envVars;
                        }
                    }

                    if (root.hasAttribute("qtVersion"))
                        sc.qtVersion = Integer.valueOf(root.getAttribute("qtVersion"));

                    if (!root.hasAttribute("flags"))
                    { // fix env vars
                        if (sc.environmentVariables != null)
                        {
                            if (sc.environmentVariables.containsKey("QML_IMPORT_PATH"))
                                sc.environmentVariables.put("QML_IMPORT_PATH", getLibsRootPath(sourceId, getRepository()) + "imports");

                            if (sc.environmentVariables.containsKey("QT_PLUGIN_PATH"))
                                sc.environmentVariables.put("QT_PLUGIN_PATH", getLibsRootPath(sourceId, getRepository()) + "plugins");
                        }
                    }
                    root.normalize();
                    Node node = root.getFirstChild();

                    HashMap<String, Library> downloadedLibraries = new HashMap<String, Library>();
                    Library.loadLibs(node, getLibsRootPath(sourceId, getRepository()), sourceId, sc.availableLibraries, downloadedLibraries, checkCrc);
                    sc.downloadedLibraries.putAll(downloadedLibraries);
                    m_sourcesCache.put(sourceId, sc);
                    putLibraries(ret, sourceId);
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        if (ret.sourcesCache.size()>0)
        {
            ret.environmentVariables.put("MINISTRO_SSL_CERTS_PATH", getMinistroSslRootPath());
            ret.environmentVariables.put("MINISTRO_ANDROID_STYLE_PATH", getMinistroStyleRootPath(displayDPI));
            ret.environmentVariables.put("QT_ANDROID_THEMES_ROOT_PATH", getMinistroStyleRootPath(-1));
            ret.environmentVariables.put("QT_ANDROID_THEME_DISPLAY_DPI", String.valueOf(displayDPI));
        }

        if (sourcesIds.size() > 1)
        {
            for (Library lib : ret.downloadedLibraries.values())
                Library.setLoadPriority(lib, ret.downloadedLibraries);
        }

        return ret;
    }

    public void removeCache(ArrayList<Integer> sourcesIds)
    {
        synchronized (this)
        {
            for( int sourceId : sourcesIds)
                m_sourcesCache.remove(sourceId);
        }
    }


    public String getRepository()
    {
        synchronized (this)
        {
            return m_repository;
        }
    }

    public void setRepository(String value)
    {
        synchronized (this)
        {
            m_repository = value;
            m_lastCheckUpdates = 0;
            saveSettings();
        }
    }

    public Long getCheckFrequency()
    {
        synchronized (this)
        {
            return m_checkFrequency / (24 * 3600 * 1000);
        }
    }

    public void setCheckFrequency(long value)
    {
        synchronized (this)
        {
            m_checkFrequency = value * 24 * 3600 * 1000;
            m_lastCheckUpdates = 0;
            saveSettings();
        }
    }

    private static Ministro m_instance = null;

    public static Ministro instance()
    {
        return m_instance;
    }

    public Ministro()
    {
        m_instance = this;
    }

    class CheckForUpdates extends AsyncTask<Void, Void, Boolean>
    {
        private double getLocalVersion(Integer sourceId) throws Exception
        {
            File file = new File(getVersionXmlFile(sourceId, m_repository));
            if (!file.exists())
                return -1;

            DocumentBuilderFactory documentFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = documentFactory.newDocumentBuilder();
            Document dom = documentBuilder.parse(new FileInputStream(file));
            Element root = dom.getDocumentElement();
            return Double.valueOf(root.getAttribute("version"));
        }

        private double getRemoteVersion(Integer sourceId) throws Exception
        {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document dom = null;
            Element root = null;
            URLConnection connection = getVersionsFileUrl(sourceId).openConnection();
            connection.setConnectTimeout(MinistroActivity.CONNECTION_TIMEOUT);
            connection.setReadTimeout(MinistroActivity.READ_TIMEOUT);
            dom = builder.parse(connection.getInputStream());
            root = dom.getDocumentElement();
            root.normalize();
            return Double.valueOf(root.getAttribute("latest"));
        }
        @Override
        protected Boolean doInBackground(Void... params)
        {
            boolean res = false;
            for (Integer sourceId : m_sources.values())
            {
                try
                {
                    double localVersion = getLocalVersion(sourceId);
                    if (localVersion > 0 && localVersion != getRemoteVersion(sourceId))
                        res = true;
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
            return res;
        }

        @SuppressWarnings("deprecation")
        @Override
        protected void onPostExecute(Boolean result)
        {
            if (!result)
                return;

            NotificationManager nm = (NotificationManager)
                    getSystemService(Context.NOTIFICATION_SERVICE);

            int icon = R.drawable.icon;
            CharSequence tickerText = getResources().getString(R.string.new_qt_libs_msg); // ticker-text
            long when = System.currentTimeMillis(); // notification time
            Context context = getApplicationContext(); // application Context
            CharSequence contentTitle = getResources().getString(R.string.ministro_update_msg); // expanded message title
            CharSequence contentText = getResources().getString(R.string.new_qt_libs_tap_msg); // expanded message text

            Intent notificationIntent = new Intent(Ministro.this,
                    MinistroActivity.class);
            PendingIntent contentIntent = PendingIntent.getActivity(Ministro.this, 0, notificationIntent, 0);

            // the next two lines initialize the Notification, using the configurations above
            Notification notification = new Notification(icon, tickerText, when);
            notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
            notification.defaults |= Notification.DEFAULT_SOUND;
            notification.defaults |= Notification.DEFAULT_LIGHTS;
            try {
                nm.notify(1, notification);
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    public String getMinistroRootPath()
    {
        return m_ministroRootPath;
    }

    public String getMinistroSslRootPath()
    {
        return m_ministroRootPath + "dl/ssl/";
    }

    public String getMinistroStyleRootPath(int displayDpi)
    {
        if (displayDpi != -1)
            return m_ministroRootPath + "dl/style/" + displayDpi + "/";
        return m_ministroRootPath + "dl/style/";
    }

    public String getVersionXmlFile(Integer sourceId, String repository)
    {
        return m_ministroRootPath + "xml/" + sourceId + "_" + repository + ".xml";
    }

    public String getLibsRootPath(Integer sourceId, String repository)
    {
        return m_ministroRootPath + "dl/" + sourceId + "/" + repository + "/";
    }

    private URL getVersionsFileUrl(Integer sourceId) throws MalformedURLException
    {
        return new URL(getSource(sourceId) + getRepository() + "/" + android.os.Build.CPU_ABI + "/android-" + android.os.Build.VERSION.SDK_INT + "/versions.xml");
    }


    public void createSourcePath(Integer sourceId, String repository)
    {
        Library.mkdirParents(m_ministroRootPath, "dl/" + sourceId+ "/" + repository, 0);
    }

    public SharedPreferences getPreferences()
    {
        return getSharedPreferences("Ministro", MODE_PRIVATE);
    }

    public Collection<Integer> getSourcesIds()
    {
        return m_sources.values();
    }

    public ArrayList<Integer> getSourcesIds(String[] sources)
    {
        ArrayList<Integer> ids = new ArrayList<Integer>();
        synchronized (this)
        {
            boolean saveSettings = false;
            for (String source : sources)
            {
                if (!source.endsWith("/"))
                    source += "/";
                if (!m_sources.containsKey(source))
                {
                    m_sources.put(source, m_nextId);
                    ids.add(m_nextId++);
                    saveSettings = true;
                }
                else
                    ids.add(m_sources.get(source));
            }
            if (saveSettings)
                saveSettings();
        }
        return ids;
    }

    public String getSource(Integer sourceId)
    {
        for (String source : m_sources.keySet())
        {
            if (m_sources.get(source) == sourceId)
                return source;
        }
        return null;
    }

    public void loadSettings()
    {
        long startTime = System.currentTimeMillis();
        synchronized (this)
        {
            try
            {
                @SuppressWarnings("resource")
                BufferedReader reader = new BufferedReader(new FileReader(getFilesDir().getAbsolutePath() + "/ministro_conf.json"));
                StringBuilder builder = new StringBuilder();
                String line = reader.readLine();
                while (line != null)
                {
                    builder.append(line);
                    builder.append("\n");
                    line = reader.readLine();
                }
                JSONObject json = new JSONObject(builder.toString());
                m_lastCheckUpdates = json.getLong(MINISTRO_CHECK_UPDATES_KEY);
                m_checkFrequency = json.getLong(MINISTRO_CHECK_FREQUENCY_KEY);
                m_repository = json.getString(MINISTRO_REPOSITORY_KEY);
                JSONArray sources = json.getJSONArray(MINISTRO_SOURCES_KEY);
                m_sources.clear();
                m_nextId = 0;
                // Clean old data
                HashSet<String> installedPackages = new HashSet<String>();
                for (PackageInfo pi : getPackageManager().getInstalledPackages(0))
                    installedPackages.add(pi.packageName);

                Log.d(TAG, installedPackages.toString());
                for (int i = 0; i < sources.length(); i++)
                {
                    JSONObject s = sources.getJSONObject(i);
                    int id = s.getInt("id");
                    if (id >= m_nextId)
                        m_nextId = id + 1;
                    m_sources.put(s.getString("url"), id);
                    if (s.has("users"))
                        loadUsers(installedPackages, id, s.getJSONArray("users"));

                    try
                    {
                        String path = getLibsRootPath(id, m_repository);
                        File f = new File(path + "style");
                        if (f.exists())
                        {
                            Library.removeAllFiles(path + "style", true);
                            f.delete();
                        }
                        f = new File(path + "ssl");
                        if (f.exists())
                        {
                            Library.removeAllFiles(path + "ssl", true);
                            f.delete();
                        }
                    }
                    catch(Exception e)
                    {
                        e.printStackTrace();
                    }
                }

                SharedPreferences preferences = getPreferences();
                boolean systemUpdate = !preferences.getString("CODENAME", "").equals(android.os.Build.VERSION.CODENAME)
                        || !preferences.getString("INCREMENTAL", "").equals(android.os.Build.VERSION.INCREMENTAL)
                        || !preferences.getString("RELEASE", "").equals(android.os.Build.VERSION.RELEASE);
                boolean cleanOldStyles = false;
                try {
                    cleanOldStyles = !preferences.getString(Session.MINISTRO_VERSION, "").equals(getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                }

                if (systemUpdate || cleanOldStyles || new File(getMinistroStyleRootPath(-1) + "style.json").exists())
                {
                    Library.removeAllFiles(getMinistroStyleRootPath(-1), true);
                    new File(getMinistroStyleRootPath(-1)).delete();
                }
                if (systemUpdate)
                {
                    Library.removeAllFiles(getMinistroSslRootPath(), true);
                    new File(getMinistroSslRootPath()).delete();
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        long endTime = System.currentTimeMillis();
        Log.i(TAG, "Load settings took " + (endTime - startTime) + " ms");
    }

    public void saveSettings()
    {
        long startTime = System.currentTimeMillis();
        synchronized (this)
        {
            try
            {
                JSONObject json = new JSONObject();
                json.put(MINISTRO_CHECK_UPDATES_KEY, m_lastCheckUpdates);
                json.put(MINISTRO_CHECK_FREQUENCY_KEY, m_checkFrequency);
                json.put(MINISTRO_REPOSITORY_KEY, m_repository);
                JSONArray sources = new JSONArray();
                for (String url : m_sources.keySet())
                {
                    JSONObject s = new JSONObject();
                    s.put("url", url);
                    final int id = m_sources.get(url);
                    s.put("id", id);
                    final JSONArray users = saveUsers(id);
                    if (users != null)
                        s.put("users", users);
                    sources.put(s);
                }
                json.put(MINISTRO_SOURCES_KEY, sources);
                OutputStreamWriter jsonWriter;
                jsonWriter = new OutputStreamWriter(new FileOutputStream(getFilesDir().getAbsolutePath() + "/ministro_conf.json"));
                jsonWriter.write(json.toString());
                jsonWriter.close();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        long endTime = System.currentTimeMillis();
        Log.i(TAG, "save settings took " + (endTime - startTime) + " ms");
    }

    private void migrateSettings()
    {
        try
        {
            // Migrate settings
            SharedPreferences preferences = getPreferences();
            m_repository = preferences.getString(MINISTRO_REPOSITORY_KEY, MINISTRO_DEFAULT_REPOSITORY);
            m_checkFrequency = preferences.getLong(MINISTRO_CHECK_FREQUENCY_KEY, 7l * 24 * 3600 * 1000);
            m_lastCheckUpdates = preferences.getLong(MINISTRO_CHECK_UPDATES_KEY, 0);//System.currentTimeMillis());
            SharedPreferences.Editor editor = preferences.edit();
            editor.remove(MINISTRO_REPOSITORY_KEY);
            editor.remove(MINISTRO_CHECK_FREQUENCY_KEY);
            editor.remove(MINISTRO_CHECK_UPDATES_KEY);
            editor.putBoolean(MINISTRO_MIGRATED_KEY, true);
            editor.commit();

            // Migrate content
            String rootPath = getFilesDir().getAbsolutePath() + "/";
            new File(rootPath + "xml/").mkdirs();
            if (new File(rootPath + "version.xml").exists())
            {
                m_sources.put(Session.NECESSITAS_SOURCE[0], m_nextId);
                new File(rootPath + "version.xml").renameTo(new File(rootPath + "xml/" + m_nextId + "_" + m_repository + ".xml"));
                Library.mkdirParents(rootPath, "dl/"+ m_nextId, 0);
                new File(rootPath + "qt").renameTo(new File(rootPath  + "dl/" + m_nextId + "/" + m_repository));
                m_nextId++;
            }
            saveSettings();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public Collection<Integer> getAllSourcesIds()
    {
        return m_sources.values();
    }

    @Override
    public void onCreate()
    {
        try {
            m_ministroRootPath = getFilesDir().getCanonicalPath() + "/";
        } catch (IOException e) {
            e.printStackTrace();
        }
        SharedPreferences preferences = getPreferences();
        if (!preferences.getBoolean(MINISTRO_MIGRATED_KEY, false))
            migrateSettings();
        loadSettings();


        if (MinistroActivity.isOnline(this) && System.currentTimeMillis() - m_lastCheckUpdates > m_checkFrequency)
        {
            m_lastCheckUpdates = System.currentTimeMillis();
            saveSettings();
            new CheckForUpdates().execute((Void[])null);
        }
        super.onCreate();
    }

}
