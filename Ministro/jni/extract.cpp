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
#include <android/log.h>
#include <extract.h>
#include <alloca.h>

#define LOG_TAG    "extractSyleInfo"
#define LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
#define LOGF(...)  __android_log_print(ANDROID_LOG_FATAL,LOG_TAG,__VA_ARGS__)

extern "C" JNIEXPORT jintArray JNICALL Java_org_kde_necessitas_ministro_ExtractStyle_extractNativeChunkInfo(JNIEnv * env, jobject  obj, Res_png_9patch* chunk)
{
        Res_png_9patch::deserialize(chunk);
        jintArray result;
        size_t size = 3+chunk->numXDivs+chunk->numYDivs+chunk->numColors;
        result = env->NewIntArray(size);
        if (!result)
            return 0;

        jint *data = (jint*)malloc(sizeof(jint)*size);
        size_t pos = 0;
        data[pos++]=chunk->numXDivs;
        data[pos++]=chunk->numYDivs;
        data[pos++]=chunk->numColors;
        for (int x = 0; x <chunk->numXDivs; x ++)
            data[pos++]=chunk->xDivs[x];
        for (int y = 0; y <chunk->numYDivs; y ++)
            data[pos++]=chunk->yDivs[y];
        for (int c = 0; c <chunk->numColors; c ++)
            data[pos++]=chunk->colors[c];
        env->SetIntArrayRegion(result, 0, size, data);
        free(data);
        return result;
}

extern "C" JNIEXPORT jintArray JNICALL Java_org_kde_necessitas_ministro_ExtractStyle_extractChunkInfo(JNIEnv * env, jobject  obj, jbyteArray chunkObj)
{
        size_t chunkSize = env->GetArrayLength(chunkObj);
        void* storage = alloca(chunkSize);
        env->GetByteArrayRegion(chunkObj, 0, chunkSize,
                                reinterpret_cast<jbyte*>(storage));

        if (!env->ExceptionCheck())
            return Java_org_kde_necessitas_ministro_ExtractStyle_extractNativeChunkInfo(env, obj, static_cast<Res_png_9patch*>(storage));
        else
            env->ExceptionClear();
        return 0;
}

// The following part was shamelessly stolen from ResourceTypes.cpp from Android's sources
static void deserializeInternal(const void* inData, Res_png_9patch* outData) {
    char* patch = (char*) inData;
    if (inData != outData) {
        memmove(&outData->wasDeserialized, patch, 4);     // copy  wasDeserialized, numXDivs, numYDivs, numColors
        memmove(&outData->paddingLeft, patch + 12, 4);     // copy  wasDeserialized, numXDivs, numYDivs, numColors
    }
    outData->wasDeserialized = true;
    char* data = (char*)outData;
    data +=  sizeof(Res_png_9patch);
    outData->xDivs = (int32_t*) data;
    data +=  outData->numXDivs * sizeof(int32_t);
    outData->yDivs = (int32_t*) data;
    data +=  outData->numYDivs * sizeof(int32_t);
    outData->colors = (uint32_t*) data;
}

Res_png_9patch* Res_png_9patch::deserialize(const void* inData)
{
    if (sizeof(void*) != sizeof(int32_t)) {
        LOGE("Cannot deserialize on non 32-bit system\n");
        return NULL;
    }
    deserializeInternal(inData, (Res_png_9patch*) inData);
    return (Res_png_9patch*) inData;
}

extern "C" JNIEXPORT jintArray JNICALL Java_org_kde_necessitas_ministro_ExtractStyle_extractNativeChunkInfo20(JNIEnv * env, jobject  obj, long addr)
{
    Res_png_9patch20* chunk = reinterpret_cast<Res_png_9patch20*>(addr);
    Res_png_9patch20::deserialize(chunk);
    jintArray result;
    size_t size = 3+chunk->numXDivs+chunk->numYDivs+chunk->numColors;
    result = env->NewIntArray(size);
    if (!result)
        return 0;

    jint *data = (jint*)malloc(sizeof(jint)*size);
    size_t pos = 0;
    data[pos++] = chunk->numXDivs;
    data[pos++] = chunk->numYDivs;
    data[pos++] = chunk->numColors;

    int32_t* xDivs = chunk->getXDivs();
    int32_t* yDivs = chunk->getYDivs();
    uint32_t* colors = chunk->getColors();

    for (int x = 0; x <chunk->numXDivs; x ++)
        data[pos++]=xDivs[x];
    for (int y = 0; y <chunk->numYDivs; y ++)
        data[pos++] = yDivs[y];
    for (int c = 0; c <chunk->numColors; c ++)
        data[pos++] = colors[c];
    env->SetIntArrayRegion(result, 0, size, data);
    free(data);
    return result;
}

extern "C" JNIEXPORT jintArray JNICALL Java_org_kde_necessitas_ministro_ExtractStyle_extractChunkInfo20(JNIEnv * env, jobject  obj, jbyteArray chunkObj)
{
    size_t chunkSize = env->GetArrayLength(chunkObj);
    void* storage = alloca(chunkSize);
    env->GetByteArrayRegion(chunkObj, 0, chunkSize,
                            reinterpret_cast<jbyte*>(storage));

    if (!env->ExceptionCheck())
        return Java_org_kde_necessitas_ministro_ExtractStyle_extractNativeChunkInfo20(env, obj, long(storage));
    else
        env->ExceptionClear();
    return 0;
}

static inline void fill9patchOffsets(Res_png_9patch20* patch) {
    patch->xDivsOffset = sizeof(Res_png_9patch20);
    patch->yDivsOffset = patch->xDivsOffset + (patch->numXDivs * sizeof(int32_t));
    patch->colorsOffset = patch->yDivsOffset + (patch->numYDivs * sizeof(int32_t));
}

Res_png_9patch20* Res_png_9patch20::deserialize(void* inData)
{
    Res_png_9patch20* patch = reinterpret_cast<Res_png_9patch20*>(inData);
    patch->wasDeserialized = true;
    fill9patchOffsets(patch);
    return patch;
}
