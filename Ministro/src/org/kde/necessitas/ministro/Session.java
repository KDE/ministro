package org.kde.necessitas.ministro;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;

public class Session
{
    // / Ministro server parameter keys
    private static final String REQUIRED_MODULES_KEY = "required.modules";
    private static final String APPLICATION_TITLE_KEY = "application.title";
    private static final String SOURCES_KEY = "sources";
    private static final String REPOSITORY_KEY = "repository";
    private static final String MINIMUM_MINISTRO_API_KEY = "minimum.ministro.api";
    private static final String MINIMUM_QT_VERSION_KEY = "minimum.qt.version";
    public static final String UPDATE_KEY = "update";
    // / Ministro server parameter keys

    // / loader parameter keys
    private static final String ERROR_CODE_KEY = "error.code";
    private static final String ERROR_MESSAGE_KEY = "error.message";
    private static final String DEX_PATH_KEY = "dex.path";
    private static final String LIB_PATH_KEY = "lib.path";
    private static final String LIBS_PATH_KEY = "libs.path";
    private static final String LOADER_CLASS_NAME_KEY = "loader.class.name";

    private static final String NATIVE_LIBRARIES_KEY = "native.libraries";
    private static final String ENVIRONMENT_VARIABLES_KEY = "environment.variables";
    private static final String APPLICATION_PARAMETERS_KEY = "application.parameters";
    private static final String QT_VERSION_PARAMETER_KEY = "qt.version.parameter";
    // / loader parameter keys

    // / loader error codes
    private static final int EC_NO_ERROR = 0;
    private static final int EC_INCOMPATIBLE = 1;
    private static final int EC_NOT_FOUND = 2;
    private static final int EC_INVALID_PARAMETERS = 3;
    private static final int EC_INVALID_QT_VERSION = 4;
    private static final int EC_DOWNLOAD_CANCELED = 5;
    // / loader error codes

    // used to check Ministro Service compatibility
    private static final int MINISTRO_MIN_API_LEVEL = 1;
    private static final int MINISTRO_MAX_API_LEVEL = 3;

    public static final String[] NECESSITAS_SOURCE = { "https://files.kde.org/necessitas/ministro/android/necessitas/" };

    private MinistroService m_service = null;
    private HashMap<String, String> m_environmentVariables = new HashMap<String, String>();
    private LinkedHashSet<String> m_applicationParams = new LinkedHashSet<String>();
    private String m_loaderClassName = null;
    private String m_pathSeparator = null;
    private IMinistroCallback m_callback = null;
    private Bundle m_parameters = null;
    private String m_repository = null;

    ArrayList<Integer> m_sourcesIds = null;
    private HashMap<String, Library> m_downloadedLibraries = new HashMap<String, Library>();
    private SparseArray<HashMap<String, Library>> m_downloadedLibrariesMap = new SparseArray<HashMap<String, Library>>();
    private final HashMap<String, Library> m_availableLibraries = new HashMap<String, Library>();

    public Session(MinistroService service, IMinistroCallback callback, Bundle parameters)
    {
        m_service = service;
        m_callback = callback;
        m_parameters = parameters;
        m_sourcesIds = m_service.getSourcesIds(getSources());
        m_pathSeparator = System.getProperty("path.separator", ":");
        long startTime = System.currentTimeMillis();
        refreshLibraries(m_service.checkCrc());
        long endTime = System.currentTimeMillis();
        Log.i(MinistroService.TAG, "refreshLibraries took " + (endTime - startTime) + " ms");
        if (!parameters.getBoolean(UPDATE_KEY, false))
        {
            startTime = System.currentTimeMillis();
            checkModulesImpl(true, null);
            endTime = System.currentTimeMillis();
            Log.i(MinistroService.TAG, "checkModulesImpl took " + (endTime - startTime) + " ms");
        }
    }

    /**
    * Implements the
    * {@link IMinistro.Stub#checkModules(IMinistroCallback, String[], String, int, int)}
    * service method.
    *
    * @param callback
    * @param parameters
    * @throws RemoteException
    */
    final void checkModulesImpl(boolean downloadMissingLibs, Result res)
    {
        if (!m_parameters.containsKey(REQUIRED_MODULES_KEY) || !m_parameters.containsKey(APPLICATION_TITLE_KEY) || !m_parameters.containsKey(MINIMUM_MINISTRO_API_KEY)
                || !m_parameters.containsKey(MINIMUM_QT_VERSION_KEY))
        {
            Bundle loaderParams = new Bundle();
            loaderParams.putInt(ERROR_CODE_KEY, EC_INVALID_PARAMETERS);
            loaderParams.putString(ERROR_MESSAGE_KEY, m_service.getResources().getString(R.string.invalid_parameters));
            try
            {
                m_callback.loaderReady(loaderParams);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            Log.e(MinistroService.TAG, "Invalid parameters: " + m_parameters.toString());
            return;
        }
        int ministroApiLevel = m_parameters.getInt(MINIMUM_MINISTRO_API_KEY);

        int qtApiLevel = m_parameters.getInt(MINIMUM_QT_VERSION_KEY);
        if (qtApiLevel > m_qtVersion) // the application needs a newer qt
                                    // version
        {
            if (m_parameters.getBoolean(QT_VERSION_PARAMETER_KEY, false))
            {
                Bundle loaderParams = new Bundle();
                loaderParams.putInt(ERROR_CODE_KEY, EC_INVALID_QT_VERSION);
                loaderParams.putString(ERROR_MESSAGE_KEY, m_service.getResources().getString(R.string.invalid_qt_version));
                try
                {
                    m_callback.loaderReady(loaderParams);
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
                Log.e(MinistroService.TAG, "Invalid qt verson");
                return;
            }
            m_parameters.putBoolean(QT_VERSION_PARAMETER_KEY, true);
            m_service.startRetrieval(this);
            return;
        }

        if (ministroApiLevel < MINISTRO_MIN_API_LEVEL || ministroApiLevel > MINISTRO_MAX_API_LEVEL)
        {
            // panic !!! Ministro service is not compatible, user should upgrade
            // Ministro package
            Bundle loaderParams = new Bundle();
            loaderParams.putInt(ERROR_CODE_KEY, EC_INCOMPATIBLE);
            loaderParams.putString(ERROR_MESSAGE_KEY, m_service.getResources().getString(R.string.incompatible_ministo_api));
            try
            {
                m_callback.loaderReady(loaderParams);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            Log.e(MinistroService.TAG, "Ministro cannot satisfy API version: " + ministroApiLevel);
            return;
        }

        // check necessitasApiLevel !!! I'm pretty sure some people will
        // completely ignore my warning
        // and they will deploying apps to Android Market, so let's try to give
        // them a chance.

        // this method is called by the activity client who needs modules.
        Bundle loaderParams = checkModules(null);
        if (!downloadMissingLibs || (loaderParams.containsKey(ERROR_CODE_KEY) && EC_NO_ERROR == loaderParams.getInt(ERROR_CODE_KEY)))
        {
            try
            {
                if (!downloadMissingLibs && res == Result.Canceled)
                {
                    loaderParams.putInt(ERROR_CODE_KEY, EC_DOWNLOAD_CANCELED);
                    loaderParams.putString(ERROR_MESSAGE_KEY, m_service.getResources().getString(R.string.ministro_canceled));
                }

                Library.mergeBundleParameters(loaderParams, ENVIRONMENT_VARIABLES_KEY, m_parameters, ENVIRONMENT_VARIABLES_KEY);
                Library.mergeBundleParameters(loaderParams, APPLICATION_PARAMETERS_KEY, m_parameters, APPLICATION_PARAMETERS_KEY);
                m_callback.loaderReady(loaderParams);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        else
        {
            // Starts a retrieval of the modules which are not readily
            // accessible.
            m_service.startRetrieval(this);
        }
    }

    HashMap<String, Library> getAvailableLibraries()
    {
        synchronized (this)
        {
            return m_availableLibraries;
        }
    }

    private String[] getSources()
    {
        if (!m_parameters.containsKey(SOURCES_KEY))
            return NECESSITAS_SOURCE;
        return m_parameters.getStringArray(SOURCES_KEY);
    }

    public ArrayList<Integer> getSourcesIds()
    {
        return m_sourcesIds;
    }

    String getRepository()
    {
        if (m_repository == null)
        {
            if (!m_parameters.containsKey(REPOSITORY_KEY))
                m_repository = m_service.getRepository();
            else
            {
                m_repository = m_parameters.getString(REPOSITORY_KEY);
                if (!m_repository.equals("stable") && !m_repository.equals("testing") && !m_repository.equals("unstable"))
                    m_repository = m_service.getRepository();
            }
        }
        return m_repository;
    }

    String getApplicationName()
    {
        return m_parameters.getString(APPLICATION_TITLE_KEY);
    }

    static void loadLibs(Node node, String rootPath, Integer sourceId, HashMap<String, Library> availableLibraries, HashMap<String, Library> downloadedLibraries, boolean checkCRC)
    {
        try
        {
            while (node != null)
            {
                if (node.getNodeType() == Node.ELEMENT_NODE)
                {
                    try
                    {
                        Library lib = Library.getLibrary((Element) node, true);
                        File file = new File(rootPath + lib.filePath);
                        lib.sourceId = sourceId;
                        if (file.exists())
                        {
                            if (checkCRC && !Library.checkCRC(file.getAbsolutePath(), lib.sha1))
                                file.delete();
                            else
                            {
                                boolean allOk = true;
                                if (lib.needs != null)
                                {
                                    for (NeedsStruct needed : lib.needs)
                                        // check if its needed files are
                                        // available
                                        if (needed.type != null && needed.type.equals("jar"))
                                        {
                                            File f = new File(rootPath + needed.filePath);
                                            if (!f.exists())
                                            {
                                                allOk = false;
                                                break;
                                            }
                                        }
                                    if (!allOk)
                                    {
                                        for (NeedsStruct needed : lib.needs)
                                            // remove all needed files
                                            if (needed.type != null && needed.type.equals("jar"))
                                            {
                                                try
                                                {
                                                    File f = new File(rootPath + needed.filePath);
                                                    if (f.exists())
                                                        f.delete();
                                                }
                                                catch (Exception e)
                                                {
                                                    e.printStackTrace();
                                                }
                                            }
                                        file.delete(); // delete the parent
                                    }
                                }
                                if (downloadedLibraries != null && allOk)
                                    downloadedLibraries.put(lib.name, lib);
                            }
                        }
                        availableLibraries.put(lib.name, lib);
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                }
                // Workaround for an unbelievable bug !!!
                try
                {
                    node = node.getNextSibling();
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                    break;
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    // when there are more sources is possible that the library load priority
    // level to be invalid, so we must re-compute it.
    void setLoadPriority(Library lib)
    {
        if (lib.touched)
            return;

        lib.touched = true;
        lib.level = 0;
        for (String dep : lib.depends)
        {
            Library l = m_downloadedLibraries.get(dep);
            if (l != null)
            {
                setLoadPriority(l);
                if (lib.level <= l.level)
                    lib.level = l.level + 1;
            }
        }
    }

    // this method reload all downloaded libraries
    void refreshLibraries(boolean checkCrc)
    {
        synchronized (this)
        {
            try
            {
                m_downloadedLibraries.clear();
                m_availableLibraries.clear();
                for (Integer sourceId : m_sourcesIds)
                {
                    File file = new File(m_service.getVersionXmlFile(sourceId));
                    if (!file.exists())
                        continue;

                    DocumentBuilderFactory documentFactory = DocumentBuilderFactory.newInstance();
                    DocumentBuilder documentBuilder = documentFactory.newDocumentBuilder();
                    Document dom = documentBuilder.parse(new FileInputStream(file));
                    Element root = dom.getDocumentElement();
                    m_versions.put(sourceId, Double.valueOf(root.getAttribute("version")));
                    m_loaderClassName = root.getAttribute("loaderClassName");
                    if (root.hasAttribute("applicationParameters"))
                    {
                        String params = root.getAttribute("applicationParameters");
                        params = params.replaceAll("MINISTRO_PATH", m_service.getFilesDir().getAbsolutePath());
                        mergeApplicationParameters(params);
                    }

                    if (root.hasAttribute("environmentVariables"))
                    {
                        String environmentVariables = root.getAttribute("environmentVariables");
                        environmentVariables = environmentVariables.replaceAll("MINISTRO_PATH", m_service.getMinistroRootPath());
                        environmentVariables = environmentVariables.replaceAll("MINISTRO_SOURCE_ROOT_PATH", m_service.getLibsRootPath(sourceId));
                        mergeEnvironmentVariables(environmentVariables);
                        m_environmentVariables.put("MINISTRO_SSL_CERTS_PATH", m_service.getMinistroSslRootPath());
                        m_environmentVariables.put("MINISTRO_ANDROID_STYLE_PATH", m_service.getMinistroStyleRootPath());
                    }
                    if (root.hasAttribute("qtVersion"))
                        m_qtVersion = Integer.valueOf(root.getAttribute("qtVersion"));

                    if (!root.hasAttribute("flags"))
                    { // fix env vars
                        if (m_environmentVariables.containsKey("QML_IMPORT_PATH"))
                            m_environmentVariables.put("QML_IMPORT_PATH", m_service.getLibsRootPath(sourceId) + "imports");

                        if (m_environmentVariables.containsKey("QT_PLUGIN_PATH"))
                            m_environmentVariables.put("QT_PLUGIN_PATH", m_service.getLibsRootPath(sourceId) + "plugins");
                    }
                    root.normalize();
                    Node node = root.getFirstChild();

                    HashMap<String, Library> downloadedLibraries = new HashMap<String, Library>();
                    loadLibs(node, m_service.getLibsRootPath(sourceId), sourceId, m_availableLibraries, downloadedLibraries, checkCrc);
                    m_downloadedLibraries.putAll(downloadedLibraries);
                    m_downloadedLibrariesMap.put(sourceId, downloadedLibraries);
                }

                if (m_sourcesIds.size() > 1)
                {
                    for (Library lib : m_downloadedLibraries.values())
                        setLoadPriority(lib);
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    private SparseArray<Double> m_versions = new SparseArray<Double>();

    public double getVersion(Integer sourceId)
    {
        if (m_versions.indexOfKey(sourceId) >= 0)
            return m_versions.get(sourceId);
        return -1;
    }

    private double m_qtVersion = 0x040800;

    public double getQtVersion()
    {
        return m_qtVersion;
    }

    public enum Result
    {
        Completed, Canceled
    }

    /**
    * Helper method for the last step of the retrieval process.
    *
    * <p>
    * Checks the availability of the requested modules and informs the
    * requesting application about it via the {@link IMinistroCallback}
    * instance.
    * </p>
    *
    */
    void retrievalFinished(Result res)
    {
        checkModulesImpl(false, res);
    }

    /**
    * Checks whether a given list of libraries are readily accessible (e.g.
    * usable by a program).
    *
    * <p>
    * If the <code>notFoundModules</code> argument is given, the method fills
    * the list with libraries that need to be retrieved first.
    * </p>
    *
    * @param libs
    * @param notFoundModules
    * @return true if all modules are available
    */
    Bundle checkModules(HashMap<String, Library> notFoundModules)
    {
        Bundle params = new Bundle();
        boolean res = true;
        ArrayList<Module> libs = new ArrayList<Module>();
        Set<String> jars = new HashSet<String>();
        for (String module : m_parameters.getStringArray(REQUIRED_MODULES_KEY))
            // don't stop on first error
            res = res & addModules(module, libs, notFoundModules, jars);

        ArrayList<String> librariesArray = new ArrayList<String>();
        // sort all libraries
        Collections.sort(libs, new ModuleCompare());
        for (Module lib : libs)
            librariesArray.add(lib.path);
        params.putStringArrayList(NATIVE_LIBRARIES_KEY, librariesArray);

        ArrayList<String> jarsArray = new ArrayList<String>();
        for (String jar : jars)
            jarsArray.add(jar);

        params.putString(DEX_PATH_KEY, Library.join(jarsArray, m_pathSeparator));
        params.putString(LOADER_CLASS_NAME_KEY, m_loaderClassName);
        try
        {
            params.putString(LIB_PATH_KEY, m_service.getLibsRootPath(m_sourcesIds.get(0)));
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        ArrayList<String> paths = new ArrayList<String>();
        for (Integer id : m_sourcesIds)
            paths.add(m_service.getLibsRootPath(id));
        params.putStringArrayList(LIBS_PATH_KEY, paths);
        params.putString(ENVIRONMENT_VARIABLES_KEY, joinEnvironmentVariables());
        params.putString(APPLICATION_PARAMETERS_KEY, Library.join(m_applicationParams, "\t"));
        params.putInt(ERROR_CODE_KEY, res ? EC_NO_ERROR : EC_NOT_FOUND);
        if (!res)
            params.putString(ERROR_MESSAGE_KEY, m_service.getResources().getString(R.string.dependencies_error));
        return params;
    }

    /**
    * Helper method for the module resolution mechanism. It deals with an
    * individual module's resolution request.
    *
    * <p>
    * The method checks whether a given <em>single</em> <code>module</code> is
    * already accessible or needs to be retrieved first. In the latter case the
    * method returns <code>false</code>.
    * </p>
    *
    * <p>
    * The method traverses a <code>module<code>'s dependencies automatically.
    * </p>
    *
    * <p>
    * In order to find out whether a <code>module</code> is accessible the
    * method consults the list of downloaded libraries. If found, an entry to
    * the <code>modules</code> list is added.
    * </p>
    *
    * <p>
    * In case the <code>module</code> is not immediately accessible and the
    * <code>notFoundModules</code> argument exists, a list of available
    * libraries is consulted to fill a list of modules which yet need to be
    * retrieved.
    * </p>
    *
    * @param module
    * @param modules
    * @param notFoundModules
    * @param jars
    * @return <code>true</code> if the given module and all its dependencies
    *         are readily available.
    */
    private boolean addModules(String module, ArrayList<Module> modules, HashMap<String, Library> notFoundModules, Set<String> jars)
    {
        // Module argument is not supposed to be null at this point.
        if (modules == null)
            return false; // we are in deep shit if this happens

        // Short-cut: If the module is already in our list of previously found
        // modules then we do not
        // need to consult the list of downloaded modules.
        for (int i = 0; i < modules.size(); i++)
        {
            if (modules.get(i).name.equals(module))
                return true;
        }

        // Consult the list of downloaded modules. If a matching entry is found,
        // it is added to the
        // list of readily accessible modules and its dependencies are checked
        // via a recursive call.
        Library library = m_downloadedLibraries.get(module);
        if (library != null)
        {
            Module m = new Module();
            m.name = library.name;
            m.path = m_service.getLibsRootPath(library.sourceId) + library.filePath;
            m.level = library.level;
            if (library.needs != null)
                for (NeedsStruct needed : library.needs)
                    if (needed.type != null && needed.type.equals("jar"))
                        jars.add(m_service.getLibsRootPath(library.sourceId) + needed.filePath);
            modules.add(m);

            boolean res = true;
            if (library.depends != null)
                for (String depend : library.depends)
                    res &= addModules(depend, modules, notFoundModules, jars);

            if (library.replaces != null)
                for (String replaceLibrary : library.replaces)
                    for (int mIt = 0; mIt < modules.size(); mIt++)
                        if (replaceLibrary.equals(modules.get(mIt).name))
                            modules.remove(mIt--);

            return res;
        }

        // Requested module is not readily accessible.
        if (notFoundModules != null)
        {
            // Checks list of modules which are known to not be readily
            // accessible and returns early to
            // prevent double entries.
            if (notFoundModules.get(module) != null)
                return false;

            // Deal with not yet readily accessible module's dependencies.
            library = m_availableLibraries.get(module);
            if (library != null)
            {
                notFoundModules.put(module, library);
                if (library.depends != null)
                    for (int depIt = 0; depIt < library.depends.length; depIt++)
                        addModules(library.depends[depIt], modules, notFoundModules, jars);
            }
        }
        return false;
    }

    /**
    * Sorter for libraries.
    *
    * Hence the order in which the libraries have to be loaded is important, it
    * is necessary to sort them.
    */
    static private class ModuleCompare implements Comparator<Module>
    {
        public int compare(Module a, Module b)
        {
            return a.level - b.level;
        }
    }

    /**
    * Helper class which allows manipulating libraries.
    *
    * It is similar to the {@link Library} class but has fewer fields.
    */
    static private class Module
    {
        String path;
        String name;
        int level;
    }

    private static void cleanLibrary(String rootPath, Library lib)
    {
        try
        {
            new File(rootPath + lib.filePath).delete();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        for (NeedsStruct n : lib.needs)
        {
            try
            {
                new File(rootPath + n.filePath).delete();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    synchronized public HashMap<String, Library> getChangedLibraries(Integer sourceId)
    {
        try
        {
            HashMap<String, Library> oldLibs = m_downloadedLibrariesMap.get(sourceId);
            File file = new File(m_service.getVersionXmlFile(sourceId));
            if (!file.exists() || oldLibs == null)
                return null;

            DocumentBuilderFactory documentFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = documentFactory.newDocumentBuilder();
            Document dom = documentBuilder.parse(new FileInputStream(file));
            Element root = dom.getDocumentElement();
            root.normalize();
            Node node = root.getFirstChild();

            HashMap<String, Library> newLibraries = new HashMap<String, Library>();
            loadLibs(node, m_service.getLibsRootPath(sourceId), sourceId, newLibraries, null, false);
            HashMap<String, Library> changedLibs = new HashMap<String, Library>();
            String rootPath = m_service.getLibsRootPath(sourceId);

            for (String library : oldLibs.keySet())
            {
                Library newLib = newLibraries.get(library);
                if (newLib != null)
                    changedLibs.put(library, newLib);
                cleanLibrary(rootPath, oldLibs.get(library));
                // TODO Check the sha1 of this library and of the needed files
                // to see if we really need to download something
                // if (newLib == null)
                // {
                // // the new libraries list doesn't contain this library
                // anymore, so we must remove it with all its needed files
                // cleanLibrary(rootPath, oldLibs.get(library));
                // continue;
                // }
                // // we must check the sha1 check sum of the both files.
                // boolean changed = false;
                // if (!newLib.sha1.equals(oldLibs.get(library).sha1))
                // changed = true;
            }
            return changedLibs;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return null;
    }

    private void mergeApplicationParameters(String parameters)
    {
        for (String parameter : parameters.split("\t"))
            if (parameter.length() > 0)
                m_applicationParams.add(parameter);
    }

    private void mergeEnvironmentVariables(String environmentVariables)
    {
        for (String envPair : environmentVariables.split("\t"))
        {
            int pos = envPair.indexOf('=');
            if (pos > 0 && pos + 2 < envPair.length())
                // TODO Check me !!!
                m_environmentVariables.put(envPair.substring(0, pos), envPair.substring(pos + 1));
        }
    }

    private String joinEnvironmentVariables()
    {
        String env = new String();
        for (String key : m_environmentVariables.keySet())
        {
            if (env.length() > 0)
                env += "\t";
            env += key + "=" + m_environmentVariables.get(key);
        }
        return env;
    }
}
