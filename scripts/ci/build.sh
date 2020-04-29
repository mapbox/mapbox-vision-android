#!/usr/bin/env bash
set -e

echo "cmake.dir=${ANDROID_HOME}/cmake/3.10.2.4988404/" >> local.properties
echo "mapboxMavenUser=${MAPBOX_USERNAME}" >> $HOME/.gradle/gradle.properties
echo "mapboxMavenToken=${MAPBOX_TOKEN}" >> $HOME/.gradle/gradle.properties

./gradlew ktlint
./gradlew assemble --console=plain
./gradlew test --console=plain
