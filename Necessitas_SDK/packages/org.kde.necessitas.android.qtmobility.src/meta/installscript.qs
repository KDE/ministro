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

// constructor
function Component()
{
    if( component.fromOnlineRepository )
    {
        if (installer.value("os") == "win")
        {
            component.addDownloadableArchive( "qtmobility-src-windows.7z" );
        }
        else
        {
            component.addDownloadableArchive( "qtmobility-src.7z" );
        }
    }
}

Component.prototype.createOperations = function()
{
    try
    {
        component.createOperations();
        var qtPath = "";
        component.addOperation( "RegisterQtCreatorSourceMapping", "@TargetDir@", "@@TEMP_PATH@@/Android/Qt/@@NECESSITAS_QT_VERSION_SHORT@@/qtmobility-src", "@TargetDir@/Android/Qt/@@NECESSITAS_QT_VERSION_SHORT@@/qtmobility-src" );
    }
    catch( e )
    {
        print( e );
    }
}

