#!/usr/bin/env bash
set -ex

echo "cmake.dir=${ANDROID_HOME}/cmake/3.10.2.4988404/" >> local.properties
echo "BUILD_CORE_FROM_SOURCE=${BUILD_CORE_FROM_SOURCE}" >> local.properties
echo "mapboxMavenUser=${MAPBOX_USERNAME}" >> $HOME/.gradle/gradle.properties
echo "mapboxMavenToken=${MAPBOX_TOKEN}" >> $HOME/.gradle/gradle.properties

./gradlew ktlint --console=plain
./gradlew assemble --console=plain
./gradlew test --console=plain