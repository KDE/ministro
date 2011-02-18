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

#include <jni.h>
#include <sys/stat.h>

jint Java_eu_licentia_necessitas_ministro_MinistroActivity_nativeChmode(JNIEnv * env, jobject obj, jstring filePath, jint mode)
{
    const char *file = (*env)->GetStringUTFChars(env, filePath, 0);
    int res = chmod(file, mode);
    (*env)->ReleaseStringUTFChars(env, filePath, file);
    return res;
}
