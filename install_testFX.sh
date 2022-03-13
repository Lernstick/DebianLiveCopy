#!/bin/sh

cd ~/Dokumente
git clone https://github.com/TestFX/TestFX.git
git checkout v4.0.16-alpha

./gradlew clean build
