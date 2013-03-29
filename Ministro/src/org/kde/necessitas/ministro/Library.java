/*
    Copyright (c) 2011-20013, BogDan Vatra <bogdan@kde.org>

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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import android.annotation.SuppressLint;
import android.os.Bundle;

class Library
{
    public String name = null;
    public String filePath = null;
    public String[] depends = null;
    public String[] replaces = null;
    public NeedsStruct[] needs = null;
    public int level = 0;
    public long size = 0;
    public boolean touched = false;

    class LibraryVersion
    {
        public int major = 0;
        public int minor = 0;
        public int patch = 0;
    }

    public LibraryVersion version;
    public String sha1 = null;
    public String url;
    public Integer sourceId;

    public static String[] getLibNames(Element libNode)
    {
        if (libNode == null)
            return null;
        NodeList list = libNode.getElementsByTagName("lib");
        ArrayList<String> libs = new ArrayList<String>();
        for (int i = 0; i < list.getLength(); i++)
        {
            if (list.item(i).getNodeType() != Node.ELEMENT_NODE)
                continue;
            Element lib = (Element) list.item(i);
            if (lib != null)
                libs.add(lib.getAttribute("name"));
        }
        String[] strings = new String[libs.size()];
        return libs.toArray(strings);
    }

    public static NeedsStruct[] getNeeds(Element libNode)
    {
        if (libNode == null)
            return null;
        NodeList list = libNode.getElementsByTagName("item");
        ArrayList<NeedsStruct> needs = new ArrayList<NeedsStruct>();

        for (int i = 0; i < list.getLength(); i++)
        {
            if (list.item(i).getNodeType() != Node.ELEMENT_NODE)
                continue;
            Element lib = (Element) list.item(i);
            if (lib != null)
            {
                NeedsStruct need = new NeedsStruct();
                try
                {
                    need.filePath = new File(lib.getAttribute("file")).getCanonicalPath();
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                    // Bad dog, it seems that somebody want's to do funny things
                    // with the file name !!!
                    continue;
                }
                need.name = lib.getAttribute("name");
                need.url = lib.getAttribute("url");
                need.sha1 = lib.getAttribute("sha1");
                need.size = Long.valueOf(lib.getAttribute("size"));
                if (lib.hasAttribute("type"))
                    need.type = lib.getAttribute("type");
                if (lib.hasAttribute("initClass"))
                    need.initClass = lib.getAttribute("initClass");
                needs.add(need);
            }
        }
        NeedsStruct[] _needs = new NeedsStruct[needs.size()];
        return needs.toArray(_needs);
    }

    @SuppressLint("DefaultLocale")
    public static Library getLibrary(Element libNode, boolean includeNeed) throws IOException
    {
        Library lib = new Library();
        // The following line may trow an exception if the file name is not good
        // !
        lib.filePath = new File(libNode.getAttribute("file")).getCanonicalPath();
        lib.name = libNode.getAttribute("name");
        lib.sha1 = libNode.getAttribute("sha1").toUpperCase();
        lib.url = libNode.getAttribute("url");
        try
        {
            lib.level = Integer.parseInt(libNode.getAttribute("level"));
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        try
        {
            lib.size = Long.parseLong(libNode.getAttribute("size"));
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        NodeList list = libNode.getElementsByTagName("depends");
        for (int i = 0; i < list.getLength(); i++)
        {
            if (list.item(i).getNodeType() != Node.ELEMENT_NODE)
                continue;
            lib.depends = getLibNames((Element) list.item(i));
            break;
        }
        list = libNode.getElementsByTagName("replaces");
        for (int i = 0; i < list.getLength(); i++)
        {
            if (list.item(i).getNodeType() != Node.ELEMENT_NODE)
                continue;
            lib.replaces = getLibNames((Element) list.item(i));
            break;
        }

        if (!includeNeed) // don't waste time.
            return lib;

        list = libNode.getElementsByTagName("needs");
        for (int i = 0; i < list.getLength(); i++)
        {
            if (list.item(i).getNodeType() != Node.ELEMENT_NODE)
                continue;
            lib.needs = getNeeds((Element) list.item(i));
            break;
        }
        return lib;
    }

    public static String convertToHex(byte[] data)
    {
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < data.length; i++)
        {
            int halfbyte = (data[i] >>> 4) & 0x0F;
            int two_halfs = 0;
            do
            {
                if ((0 <= halfbyte) && (halfbyte <= 9))
                    buf.append((char) ('0' + halfbyte));
                else
                    buf.append((char) ('a' + (halfbyte - 10)));
                halfbyte = data[i] & 0x0F;
            } while (two_halfs++ < 1);
        }
        return buf.toString();
    }

    public static boolean checkCRC(String fileName, String sha1)
    {
        try
        {
            byte[] tmp = new byte[2048];
            MessageDigest digester = MessageDigest.getInstance("SHA-1");
            int downloaded;
            FileInputStream inFile = new FileInputStream(new File(fileName));
            while ((downloaded = inFile.read(tmp)) != -1)
            {
                digester.update(tmp, 0, downloaded);
            }
            inFile.close();
            return sha1.equalsIgnoreCase(convertToHex(digester.digest()));
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return false;
    }

    public static String mkdirParents(String rootPath, String filePath, int skip)
    {
        String[] paths = filePath.split("/");
        String path = "";
        for (int pit = 0; pit < paths.length - skip; pit++)
        {
            if (paths[pit].length() == 0)
                continue;
            path += "/" + paths[pit];
            File dir = new File(rootPath + path);
            dir.mkdir();
            MinistroActivity.nativeChmode(rootPath + path, 0755);
        }
        return rootPath + path;
    }

    public static void removeAllFiles(String path)
    {
        File f = new File(path);
        if (!f.exists())
            return;
        String files[] = f.list();
        if (!path.endsWith("/"))
            path += "/";
        for (int i = 0; i < files.length; i++)
        {
            try
            {
                new File(path + files[i]).delete();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    public static String join(Collection<String> s, String delimiter)
    {
        if (s == null || s.isEmpty())
            return "";
        Iterator<String> iter = s.iterator();
        StringBuilder builder = new StringBuilder(iter.next());
        while (iter.hasNext())
        {
            builder.append(delimiter).append(iter.next());
        }
        return builder.toString();
    }

    public static void mergeBundleParameters(Bundle out, String outKey, Bundle in, String inKey)
    {
        if (!in.containsKey(inKey))
            return;

        String value = null;
        if (out.containsKey(outKey))
            value = out.getString(outKey);

        if (value != null && value.length() > 0 && value.charAt(value.length() - 1) != '\t')
            value = value + "\t";

        value = value + in.getString(inKey);
        out.putString(outKey, value);
    }
};

class NeedsStruct
{
    public String name = null;
    public String filePath = null;
    public String sha1 = null;
    public String url = null;
    public String type = null;
    public String initClass = null;
    public long size = 0;
};
