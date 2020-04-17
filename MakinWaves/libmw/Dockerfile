FROM rust:1.42

RUN wget -nv https://dl.google.com/android/repository/android-ndk-r21-linux-x86_64.zip -O /tmp/ndk.zip \
    && unzip -q /tmp/ndk.zip -d /opt \
    && rm /tmp/ndk.zip

RUN rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android

# Use environment variables instead of a config file so that we can override CARGO_HOME
ENV CARGO_TARGET_AARCH64_LINUX_ANDROID_AR="/opt/android-ndk-r21/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android-ar" \
    CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="/opt/android-ndk-r21/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android29-clang" \
    CARGO_TARGET_ARMV7_LINUX_ANDROIDEABI_AR="/opt/android-ndk-r21/toolchains/llvm/prebuilt/linux-x86_64/bin/arm-linux-androideabi-ar" \
    CARGO_TARGET_ARMV7_LINUX_ANDROIDEABI_LINKER="/opt/android-ndk-r21/toolchains/llvm/prebuilt/linux-x86_64/bin/armv7a-linux-androideabi29-clang" \
    CARGO_TARGET_I686_LINUX_ANDROID_AR="/opt/android-ndk-r21/toolchains/llvm/prebuilt/linux-x86_64/bin/i686-linux-android-ar" \
    CARGO_TARGET_I686_LINUX_ANDROID_LINKER="/opt/android-ndk-r21/toolchains/llvm/prebuilt/linux-x86_64/bin/i686-linux-android29-clang" \
    CARGO_TARGET_X86_64_LINUX_ANDROID_AR="/opt/android-ndk-r21/toolchains/llvm/prebuilt/linux-x86_64/bin/x86_64-linux-android-ar" \
    CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER="/opt/android-ndk-r21/toolchains/llvm/prebuilt/linux-x86_64/bin/x86_64-linux-android29-clang"
