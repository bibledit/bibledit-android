#!/bin/bash


# Run this script from the directory where it is located.
# Example: ./refresh.sh


# Export environment variables to find the Android SDK and NDK tools. Todo go out.
# export ANDROID_HOME=~/scr/android-sdk-macosx
# export PATH=$PATH:~/scr/android-sdk-macosx/platform-tools:~/scr/android-sdk-macosx/tools:~/scr/android-ndk-r10e


echo Define the assets
ASSETSFOLDER=app/src/main/assets
EXTERNALFOLDER=$ASSETSFOLDER/external


echo Put all the code of the Bibledit kernel into the following folder:
echo $EXTERNALFOLDER
echo This is in preparation for subsequent steps.
rsync -a --delete --exclude .git ../../cloud/ $EXTERNALFOLDER/
if [ $? -ne 0 ]; then exit; fi


echo Clean the code up a bit by removing a couple of things.
pushd $EXTERNALFOLDER
rm -f *.gz
popd


echo Build several databases and other data for inclusion with the Android package.
echo The reason for this is that building them on Android takes a lot of time during the setup phase.
echo To include pre-built data, that will speed up the setup phase of Bibledit on Android.
echo This gives a better user experience.
echo At the end, it removes the journal entries that were logged in the process.
pushd $EXTERNALFOLDER
./configure
make --jobs=4
if [ $? -ne 0 ]; then exit; fi
./generate . locale
if [ $? -ne 0 ]; then exit; fi
./generate . mappings
if [ $? -ne 0 ]; then exit; fi
./generate . versifications
if [ $? -ne 0 ]; then exit; fi
popd


echo Clean the Bibledit kernel source code.
pushd $EXTERNALFOLDER
make distclean
if [ $? -ne 0 ]; then exit; fi
popd


exit



# Refresh the Bibledit source code in the jni folder.
rsync -a --exclude '*.o' --delete ../../cloud/* jni
pushd jni
./configure --enable-android
rm -f bibledit
rm -r autom4te.cache
rm dev
rm -f *.a
rm -f *.tar
rm -f *.tar.gz
rm reconfigure
rm -f server
rm -f unittest
rm -f generate
rm valgrind
rm -rf cloud.xcodeproj
rm -r executable
rm aclocal.m4
rm AUTHORS
rm ChangeLog
rm compile
rm config.guess
rm config.h.in
rm config.log
rm config.status
rm config.sub
rm configure
rm configure.ac
rm COPYING
rm depcomp
rm DEVELOP
rm INSTALL
rm install-sh
rm Makefile
rm Makefile.in
rm missing
rm NEWS
rm README
rm stamp-h1
rm -rf sources/hebrewlexicon
rm -rf sources/morphgnt
rm -rf sources/morphhb
rm -rf sources/sblgnt
rm sources/oshb.xml.gz
rm -rf unittests
rm config/local.server.key
popd


# Remove some data so that the .apk does not exceed 50 Mbytes - the limit Google Play puts on it.
# Update: The new limit is now 100 Mbytes.


# Android does not provide 'stoi' in C++.
sed -i.bak '/HAVE_STOI/d' jni/config.h
# No libsword.
sed -i.bak '/HAVE_SWORD/d' jni/config.h
# No file-upload possible from web view.
sed -i.bak '/CONFIG_ENABLE_FILE_UPLOAD/d' jni/config/config.h
# Android does not need BSD memory profiling calls.
sed -i.bak '/HAVE_MACH_MACH/d' jni/config.h
# Cleanup
rm jni/config.h.bak
rm jni/config/config.h.bak


# The following command saves all source files from Makefile.am to file.
# It uses several steps to obtain the result:
# * Obtain source files between the correct patterns.
# * Remove first line.
# * Remove last line.
# * Remove tabs.
# * Remove new lines.
# * Remove backslashes.
sed -n "/libbibledit_a_SOURCES/,/bin_PROGRAMS/p" jni/Makefile.am | tail -n +2 | sed '$d' | strings | tr -d '\n' | sed 's/\\//g' > jni/sources.txt


# Create Android.mk Makefile from Android.am.
sed "s|SOURCEFILES|$(cat jni/sources.txt)|" jni/Android.am > jni/Android.mk
rm jni/sources.txt


# Build native code.
# https://developer.android.com/tools/sdk/ndk/index.html
ndk-build clean
ndk-build NDK_DEBUG=1
