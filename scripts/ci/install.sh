#!/usr/bin/env bash
set -e

wget https://dl.google.com/android/repository/android-ndk-r18b-linux-x86_64.zip
unzip -qq android-ndk-r18b-linux-x86_64.zip
export ANDROID_NDK_HOME=`pwd`/android-ndk-r18b
export PATH=$PATH:${ANDROID_NDK_HOME}

BOOST_VERSION="boost_1_69_0"
wget -O ${BOOST_VERSION}.tar.gz https://dl.bintray.com/boostorg/release/1.69.0/source/${BOOST_VERSION}.tar.gz
tar xzf ${BOOST_VERSION}.tar.gz
export ROOT_BOOST_PATH=`pwd`/${BOOST_VERSION}/