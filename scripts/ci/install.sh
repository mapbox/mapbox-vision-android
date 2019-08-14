#!/usr/bin/env bash
set -e

echo "> Working dir: `pwd`"

wget https://dl.google.com/android/repository/android-ndk-r18b-linux-x86_64.zip
unzip -qq android-ndk-r18b-linux-x86_64.zip
export ANDROID_NDK_HOME=`pwd`/android-ndk-r18b
export PATH=$PATH:${ANDROID_NDK_HOME}

BOOST_VERSION="boost_1_69_0"
wget -O ${BOOST_VERSION}.tar.gz https://dl.bintray.com/boostorg/release/1.69.0/source/${BOOST_VERSION}.tar.gz
tar xzf ${BOOST_VERSION}.tar.gz
export ROOT_BOOST_PATH=`pwd`/${BOOST_VERSION}/

echo "> Cloning mapbox-vision"
git clone git@github.com:mapbox/mapbox-vision.git
git checkout origin/dev

export MAPBOX_VISION_DIR=`pwd`/mapbox-vision

echo "> MAPBOX_VISION_DIR=${MAPBOX_VISION_DIR}"

echo "CORE_VISION_SDK=${MAPBOX_VISION_DIR}/SDK/Platforms/Android/sdk" >> $HOME/.gradle/gradle.properties
echo "CORE_VISION_SAFETY=${MAPBOX_VISION_DIR}/SDK/Platforms/Android/safety" >> $HOME/.gradle/gradle.properties
echo "CORE_VISION_AR=${MAPBOX_VISION_DIR}/SDK/Platforms/Android/ar" >> $HOME/.gradle/gradle.properties