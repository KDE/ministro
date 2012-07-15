# remove things which are not ready for release
function removeUnusedPackages
{
    # x86 support needs much more love than just a compilation, QtCreator needs to handle it correctly
    rm -fr $TEMP_PATH/out/necessitas/sdk_src/org.kde.necessitas.android.qt.x86

    # Wait until Linaro toolchain is ready
    rm -fr $TEMP_PATH/out/necessitas/sdk_src/org.kde.necessitas.misc.ndk.ma_r6

    # Do we really need this packages ?
    rm -fr $TEMP_PATH/out/necessitas/sdk_src/org.kde.necessitas.misc.ndk.gdb_head
    rm -fr $TEMP_PATH/out/necessitas/sdk_src/org.kde.necessitas.misc.host_gdb_head
    rm -fr $TEMP_PATH/out/necessitas/sdk_src/org.kde.necessitas.misc.ndk.gdb_7_3
    rm -fr $TEMP_PATH/out/necessitas/sdk_src/org.kde.necessitas.misc.ndk.r6

    # OpenJDK needs to be handled into QtCeator
    rm -fr $TEMP_PATH/out/necessitas/sdk_src/org.kde.necessitas.misc.openjdk

    # Webkit for alpha4 is coming with qt framework
    rm -fr $TEMP_PATH/out/necessitas/sdk_src/org.kde.necessitas.android.qtwebkit*
}
