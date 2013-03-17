/*
    Copyright (c) 2011, BogDan Vatra <bog_dan_ro@yahoo.com>

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
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import org.json.JSONArray;
import org.json.JSONObject;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.SparseArray;

public class MinistroService extends Service
{
    public static final String TAG = "MinistroService";

    private static final String MINISTRO_CHECK_UPDATES_KEY = "LASTCHECK";
    private static final String MINISTRO_CHECK_FREQUENCY_KEY = "CHECKFREQUENCY";
    private static final String MINISTRO_REPOSITORY_KEY = "REPOSITORY";
    private static final String MINISTRO_MIGRATED_KEY = "MIGRATED";
    private static final String MINISTRO_DEFAULT_REPOSITORY = "stable";
    private static final String MINISTRO_SOURCES_KEY = "SOURCES";

    private HashMap<String, Integer> m_sources = new HashMap<String, Integer>();
    private String m_repository = null;
    private long m_checkUpdates = 0;
    private long m_checkFrequency = 7l * 24 * 3600 * 1000; // 7 days
    private int m_nextId = 0;

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
            m_checkUpdates = 0;
            saveSettings();
        }
    }

    public Long getCheckFrequency()
    {
        synchronized (this)
        {
            return m_checkFrequency;
        }
    }

    public void setCheckFrequency(long value)
    {
        synchronized (this)
        {
            m_checkFrequency = value;
            m_checkUpdates = 0;
            saveSettings();
        }
    }

    // MinistroService instance, its used by MinistroActivity to directly access
    // services data (e.g. libraries)
    private static MinistroService m_instance = null;

    public static MinistroService instance()
    {
        return m_instance;
    }

    public MinistroService()
    {
        m_instance = this;
    }

    private int m_actionId = 0; // last actions id
    private Handler m_handler = null;

    private SparseArray<Session> m_sessions = new SparseArray<Session>();

    // class CheckForUpdates extends AsyncTask<Void, Void, Void>
    // {
    // @Override
    // protected void onPreExecute()
    // {
    // if
    // (m_version<MinistroActivity.downloadVersionXmlFile(MinistroService.this,
    // true))
    // {
    // NotificationManager nm = (NotificationManager)
    // getSystemService(Context.NOTIFICATION_SERVICE);
    //
    // int icon = R.drawable.icon;
    // CharSequence tickerText =
    // getResources().getString(R.string.new_qt_libs_msg); // ticker-text
    // long when = System.currentTimeMillis(); // notification time
    // Context context = getApplicationContext(); // application Context
    // CharSequence contentTitle =
    // getResources().getString(R.string.ministro_update_msg); // expanded
    // message title
    // CharSequence contentText =
    // getResources().getString(R.string.new_qt_libs_tap_msg); // expanded
    // message text
    //
    // Intent notificationIntent = new Intent(MinistroService.this,
    // MinistroActivity.class);
    // PendingIntent contentIntent =
    // PendingIntent.getActivity(MinistroService.this, 0, notificationIntent,
    // 0);
    //
    // // the next two lines initialize the Notification, using the
    // configurations above
    // Notification notification = new Notification(icon, tickerText, when);
    // notification.setLatestEventInfo(context, contentTitle, contentText,
    // contentIntent);
    // notification.defaults |= Notification.DEFAULT_SOUND;
    // notification.defaults |= Notification.DEFAULT_LIGHTS;
    // try {
    // nm.notify(1, notification);
    // } catch(Exception e) {
    // e.printStackTrace();
    // }
    // }
    // }
    //
    // @Override
    // protected Void doInBackground(Void... params) {
    // return null;
    // }
    // }

    /**
     * Creates and sets up a {@link MinistroActivity} to retrieve the modules
     * specified in the <code>session</code> argument.
     * 
     * @param session
     */

    synchronized public void startRetrieval(Session session)
    {
        int id = m_actionId++;
        m_sessions.put(id, session);
        final Intent intent = new Intent(MinistroService.this, MinistroActivity.class);
        intent.putExtra("id", id);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        boolean failed = false;
        try
        {
            m_handler.postDelayed(new Runnable()
            {
                public void run()
                {
                    MinistroService.this.startActivity(intent);
                }
            }, 100);
        }
        catch (Exception e)
        {
            failed = true;
            e.printStackTrace();
        }
        finally
        {
            // Removes the dead Activity from our list as it will never finish
            // by itself.
            if (failed)
            {
                m_sessions.remove(id);
                if (0 == m_sessions.size())
                    id = 0;
            }
        }
    }

    public Session getSession(int id)
    {
        if (m_sessions.indexOfKey(id) >= 0)
            return m_sessions.get(id);

        return null;
    }

    /**
     * Called by a finished {@link MinistroActivity} in order to let the service
     * notify the application which caused the activity about the result of the
     * retrieval.
     * 
     * @param id
     */
    void retrievalFinished(int id, Session.Result res)
    {

        if (m_sessions.indexOfKey(id) >= 0)
        {
            m_sessions.get(id).retrievalFinished(res);
            m_sessions.remove(id);
            if (m_sessions.size() == 0)
                m_actionId = 0;
        }
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
                if (!m_sources.containsKey(source))
                {
                    m_sources.put(source, m_nextId);
                    ids.add(m_nextId++);
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
                m_checkUpdates = json.getLong(MINISTRO_CHECK_UPDATES_KEY);
                m_checkFrequency = json.getLong(MINISTRO_CHECK_FREQUENCY_KEY);
                m_repository = json.getString(MINISTRO_REPOSITORY_KEY);
                JSONArray sources = json.getJSONArray(MINISTRO_SOURCES_KEY);
                m_sources.clear();
                m_nextId = 0;
                for (int i = 0; i < sources.length(); i++)
                {
                    JSONObject s = sources.getJSONObject(i);
                    int id = s.getInt("id");
                    if (id >= m_nextId)
                        m_nextId = id + 1;
                    m_sources.put(s.getString("url"), id);
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    public void saveSettings()
    {
        synchronized (this)
        {
            try
            {
                JSONObject json = new JSONObject();
                json.put(MINISTRO_CHECK_UPDATES_KEY, m_checkUpdates);
                json.put(MINISTRO_CHECK_FREQUENCY_KEY, m_checkFrequency);
                json.put(MINISTRO_REPOSITORY_KEY, m_repository);
                JSONArray sources = new JSONArray();
                for (String url : m_sources.keySet())
                {
                    JSONObject s = new JSONObject();
                    s.put("url", url);
                    s.put("id", m_sources.get(url));
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
    }

    void migrateSettings()
    {
        try
        {
            // Migrate settings
            SharedPreferences preferences = getSharedPreferences("Ministro", MODE_PRIVATE);
            m_repository = preferences.getString(MINISTRO_REPOSITORY_KEY, MINISTRO_DEFAULT_REPOSITORY);
            m_checkFrequency = preferences.getLong(MINISTRO_CHECK_FREQUENCY_KEY, 7l * 24 * 3600 * 1000);
            m_checkUpdates = preferences.getLong(MINISTRO_CHECK_UPDATES_KEY, 0);
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
                new File(rootPath + "version.xml").renameTo(new File(rootPath + "xml/" + m_nextId + ".xml"));
                Library.mkdirParents(rootPath, "dl", 0);
                new File(rootPath + "qt/style").renameTo(new File(rootPath  + "dl/style"));
                new File(rootPath + "qt/ssl").renameTo(new File(rootPath  + "dl/ssl"));
                new File(rootPath + "qt").renameTo(new File(rootPath  + "dl/" + m_nextId));
                m_nextId++;
            }
            saveSettings();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public void onCreate()
    {
        m_handler = new Handler();
        SharedPreferences preferences = getSharedPreferences("Ministro", MODE_PRIVATE);
        if (!preferences.getBoolean(MINISTRO_MIGRATED_KEY, false))
            migrateSettings();
        else
            loadSettings();

        // m_versionXmlFile = getFilesDir().getAbsolutePath()+"/version.xml";
        // m_qtLibsRootPath = getFilesDir().getAbsolutePath()+"/qt/";
        // m_pathSeparator = System.getProperty("path.separator", ":");
        // SharedPreferences preferences=getSharedPreferences("Ministro",
        // MODE_PRIVATE);
        // long lastCheck = preferences.getLong(MINISTRO_CHECK_UPDATES_KEY,0);
        // long checkFrequency =
        // preferences.getLong(MINISTRO_CHECK_FREQUENCY_KEY,7l*24*3600*1000); //
        // check once per week by default
        // if (MinistroActivity.isOnline(this) &&
        // System.currentTimeMillis()-lastCheck>checkFrequency)
        // {
        // refreshLibraries(true);
        // SharedPreferences.Editor editor= preferences.edit();
        // editor.putLong(MINISTRO_CHECK_UPDATES_KEY,System.currentTimeMillis());
        // editor.commit();
        // new CheckForUpdates().execute((Void[])null);
        // }
        // else
        // refreshLibraries(false);
        super.onCreate();
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return new IMinistro.Stub()
        {
            public void requestLoader(IMinistroCallback callback, Bundle parameters)
            {
                try
                {
                    new Session(MinistroService.this, callback, parameters);
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        };
    }
}
