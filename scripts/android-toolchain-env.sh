#!/usr/bin/env bash
# Source this file to set up the Android/Rust build toolchain in your shell:
#   source scripts/android-toolchain-env.sh
#
# Installed 2026-08-20 for local ./gradlew builds — see
# ANDROID_TOOLCHAIN_SETUP.md (sent to you separately) for what was installed
# and how to remove it.

export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_NDK_HOME="$HOME/Android/Sdk/ndk/29.0.14206865"
export PATH="$JAVA_HOME/bin:$HOME/.cargo/bin:$ANDROID_HOME/platform-tools:$PATH"
