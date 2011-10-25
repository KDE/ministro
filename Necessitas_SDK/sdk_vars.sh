OSTYPE_MAJOR=${OSTYPE//[0-9.]/}

MINISTRO_VERSION="0.3" #Ministro repo version

# Only for Linux, Windows and Mac use 4.8.
if [ "$OSTYPE_MAJOR" = "linux-gnu" ] ; then
    HOST_QT_BRANCH="remotes/upstream/tags/v4.7.4"
else
    HOST_QT_BRANCH="refs/remotes/origin/4.8"
fi

CHECKOUT_BRANCH="unstable"

NECESSITAS_QT_CREATOR_VERSION="2.3.81"

# archivegen gives much worse compression.
if [ "$OSTYPE_MAJOR" = "msys" ] ; then
    JOBS=`expr $NUMBER_OF_PROCESSORS + 2`
else
    if [ "$OSTYPE_MAJOR" = "darwin" ] ; then
        JOBS=`sysctl -n hw.ncpu`
        JOBS=`expr $JOBS + $JOBS + 2`
    else
        JOBS=`cat /proc/cpuinfo | grep processor | wc -l`
        JOBS=`expr $JOBS + 2`
    fi
fi

if [ "$OSTYPE_MAJOR" = "linux-gnu" ] ; then
    EXTERNAL_7Z=7z
    EXTERNAL_7Z_PARAMS="a -t7z -mx=9 -mmt=$JOBS"
else
    EXTERNAL_7Z=7za
    EXTERNAL_7Z_PARAMS="a -t7z -mx=9"
fi

# Qt Framework versions
NECESSITAS_QT_VERSION_SHORT=4763 #Necessitas Qt Framework Version
NECESSITAS_QT_VERSION="4.7.63" #Necessitas Qt Framework Long Version

NECESSITAS_QTWEBKIT_VERSION="2.2" #Necessitas QtWebkit Version

NECESSITAS_QTMOBILITY_VERSION="1.2.0" #Necessitas QtMobility Version

# NDK variables
BUILD_ANDROID_GIT_NDK=0 # Latest and the greatest NDK built from sources
ANDROID_NDK_MAJOR_VERSION=r6 # NDK major version, used by package name (and ma ndk)
ANDROID_NDK_VERSION=r6b # NDK full package version
USE_MA_NDK=1
# ANDROID_GCC_VERSION_MAJOR is ??? in the folder name
# prebuilt/linux-x86/lib/gcc/arm-linux-androideabi/???
# ANDROID_GCC_VERSION_MAJOR=4.6.2
# ANDROID_GCC_VERSION is ??? in the folder name
# toolchains/arm-linux-androideabi-???/prebuilt
# ANDROID_GCC_VERSION=4.6-2011.10
ANDROID_GCC_VERSION_MAJOR=4.4.3
ANDROID_GCC_VERSION=4.4.3

# SDK variables
ANDROID_SDK_VERSION=r14
ANDROID_PLATFORM_TOOLS_VERSION=r08
ANDROID_API_4_VERSION=1.6_r03
ANDROID_API_5_VERSION=2.0_r01
ANDROID_API_6_VERSION=2.0.1_r01
ANDROID_API_7_VERSION=2.1_r03
ANDROID_API_8_VERSION=2.2_r03
ANDROID_API_9_VERSION=2.3.1_r02
ANDROID_API_10_VERSION=2.3.3_r02
ANDROID_API_11_VERSION=3.0_r02
ANDROID_API_12_VERSION=3.1_r03
ANDROID_API_13_VERSION=3.2_r01
ANDROID_API_14_VERSION=14_r01

# Make debug versions of host applications (Qt Creator and installer).
MAKE_DEBUG_HOST_APPS=0

MAKE_DEBUG_GDBSERVER=0
