OSTYPE_MAJOR=${OSTYPE//[0-9.]/}
NDK_VER=r6b
GCC_VER=4.6.3
GCC_VER_LINARO=4.6-2012.03

# Refers to the gdb branches and folder structure on
# git://gitorious.org/toolchain-mingw-android/mingw-android-toolchain-gdb.git
GDB_BRANCH=fsf_head
GDB_ROOT_PATH=
# This is the name given to the created package.
GDB_VER=7.4.50.20111216
# We apply ndk r6 patches and can't use the cutting edge version of ndk gcc anyway (due to crtbegin_so.o, crtend_so.o changes)
GCC_GIT_DATE=2011-02-27
GCC_LINARO=1

# Just before significant changes to this repo (neccessary libs - e.g. libdl.so - removed)
PLATFORM_GIT_DATE=2011-10-09

# This is the latest Google version, we copy new bits from GCC_VER into this (GDB only).
GCC_VER_OTHER=4.4.3

