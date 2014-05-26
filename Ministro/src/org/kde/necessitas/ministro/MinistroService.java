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

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.SparseArray;

public class MinistroService extends Service
{
    private static final String MINISTRO_CHECK_CRC_KEY = "CHECK_CRC";

    private boolean m_checkCrc = true;

    public boolean checkCrc()
    {
        return m_checkCrc;
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

    public Session getUpdateSession()
    {
        synchronized (this)
        {
            if (m_sessions.size() == 0)
            {
                Bundle params = new Bundle();
                params.putBoolean(Session.UPDATE_KEY, true);
                Session session = new Session(this, null, params, null);
                m_sessions.put(m_actionId++, session);
                return session;
            }
            return null;
        }
    }

    private void startActivity(boolean refreshLibs)
    {
        int id = m_sessions.keyAt(0);
        if (refreshLibs)
            getSession(id).refreshLibraries(false);
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
                retrievalFinished(id, Session.Result.Canceled);
        }
    }

    private void showActivity()
    {
        Intent intent = new Intent(MinistroService.this, MinistroActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
    /**
    * Creates and sets up a {@link MinistroActivity} to retrieve the modules
    * specified in the <code>session</code> argument.
    *
    * @param session
    */

    public void startRetrieval(Session session)
    {
        synchronized (this)
        {
            int id = m_actionId++;
            boolean startActivity = m_sessions.size() == 0;
            m_sessions.put(id, session);
            if (startActivity)
                startActivity(false);
            else
                showActivity();
        }
    }

    public Session getSession(int id)
    {
        synchronized (this)
        {
            if (m_sessions.indexOfKey(id) >= 0)
                return m_sessions.get(id);
            return null;
        }
    }

    /**
    * Called by a finished {@link MinistroActivity} in order to let the service
    * notify the application which caused the activity about the result of the
    * retrieval.
    *
    */
    void retrievalFinished(int id, Session.Result res)
    {
        synchronized (this)
        {
            if (m_sessions.indexOfKey(id) >= 0)
            {
                Session s = m_sessions.get(id);
                m_sessions.remove(id);
                s.retrievalFinished(res);
                if (m_sessions.size() == 0)
                    m_actionId = 0;
                else
                    startActivity(true);
            }
        }
    }


    Ministro m_ministro = null;
    @Override
    public void onCreate()
    {
        m_ministro = Ministro.instance();
        m_handler = new Handler();
        SharedPreferences preferences = m_ministro.getPreferences();
        m_checkCrc = preferences.getBoolean(MINISTRO_CHECK_CRC_KEY, true);
        if (!m_checkCrc)
        {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(MINISTRO_CHECK_CRC_KEY, true);
            editor.commit();
        }
        super.onCreate();
    }

    @Override
    public void onDestroy()
    {
        SharedPreferences preferences = m_ministro.getPreferences();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(MINISTRO_CHECK_CRC_KEY, false);
        editor.commit();
        m_ministro.saveSettings();
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
                    if (m_ministro.getMinistroRootPath() != null)
                        new Session(MinistroService.this, callback, parameters, getPackageManager().getPackagesForUid(Binder.getCallingUid()));
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        };
    }
}
