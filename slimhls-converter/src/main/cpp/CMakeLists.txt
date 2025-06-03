cmake_minimum_required(VERSION 3.18.1)

project(ffmpeg_android LANGUAGES C CXX)

if(NOT FFMPEG_SOURCE_DIR)
    message(FATAL_ERROR "FFMPEG_SOURCE_DIR not defined. Pass -DFFMPEG_SOURCE_DIR from Gradle.")
endif()
set(FFMPEG_BUILD_DIR "${CMAKE_BINARY_DIR}/ffmpeg-build/${ANDROID_ABI}")
set(FFMPEG_INSTALL_DIR "${FFMPEG_BUILD_DIR}/install")
set(FFMPEG_OUTPUT "${FFMPEG_INSTALL_DIR}/bin/ffmpeg")

file(MAKE_DIRECTORY "${FFMPEG_BUILD_DIR}")
file(MAKE_DIRECTORY "${FFMPEG_INSTALL_DIR}")

# Validate ANDROID_NDK
if(NOT ANDROID_NDK)
    message(FATAL_ERROR "ANDROID_NDK not defined. Set ANDROID_NDK_HOME environment variable or pass -DANDROID_NDK.")
endif()

# Detect ABI
if(NOT ANDROID_ABI)
    message(FATAL_ERROR "ANDROID_ABI not defined")
endif()

# UPX configuration
if(NOT UPX_PATH OR "${UPX_PATH}" STREQUAL "")
    message(WARNING "UPX_PATH is not set or empty. Skipping UPX compression.")
    set(UPX_AVAILABLE FALSE)
else()
    if(EXISTS "${UPX_PATH}")
        message(STATUS "UPX compression enabled using: ${UPX_PATH}")
        set(UPX_AVAILABLE TRUE)
    else()
        message(WARNING "UPX path '${UPX_PATH}' not found. Skipping compression.")
        set(UPX_AVAILABLE FALSE)
    endif()
endif()

# Detect architecture and toolchain
if(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(ARCH arm)
    set(CROSS_PREFIX armv7a-linux-androideabi)
elseif(ANDROID_ABI STREQUAL "arm64-v8a")
    set(ARCH arm64)
    set(CROSS_PREFIX aarch64-linux-android)
elseif(ANDROID_ABI STREQUAL "x86")
    set(ARCH x86)
    set(CROSS_PREFIX i686-linux-android)
elseif(ANDROID_ABI STREQUAL "x86_64")
    set(ARCH x86_64)
    set(CROSS_PREFIX x86_64-linux-android)
else()
    message(FATAL_ERROR "Unsupported ABI: ${ANDROID_ABI}")
endif()

set(API 19) # available in ndk '25.2.9519653'
if(ANDROID_ABI STREQUAL "arm64-v8a" OR ANDROID_ABI STREQUAL "x86_64")
    set(API 21) # use 21 as 19 api level not available for those ANDROID_ABI's
endif()
if(NOT HOST_TAG)
    message(FATAL_ERROR "HOST_TAG not defined. Pass -DHOST_TAG from Gradle.")
endif()
set(TOOLCHAIN "${ANDROID_NDK}/toolchains/llvm/prebuilt/${HOST_TAG}/bin")
set(SYSROOT "${ANDROID_NDK}/toolchains/llvm/prebuilt/${HOST_TAG}/sysroot")
message(STATUS "SYSROOT: ${SYSROOT}")
set(CC "${TOOLCHAIN}/${CROSS_PREFIX}${API}-clang")
set(CXX "${TOOLCHAIN}/${CROSS_PREFIX}${API}-clang++")
set(AR "${TOOLCHAIN}/llvm-ar")
set(AS "${TOOLCHAIN}/${CROSS_PREFIX}${API}-clang")
set(NM "${TOOLCHAIN}/llvm-nm")
set(STRIP "${TOOLCHAIN}/llvm-strip")
set(RANLIB "${TOOLCHAIN}/llvm-ranlib")

# Ensure FFmpeg source exists
if(NOT EXISTS "${FFMPEG_SOURCE_DIR}/configure")
    message(FATAL_ERROR "FFmpeg source directory ${FFMPEG_SOURCE_DIR} does not contain configure script. Run prepareFFmpegSource task.")
endif()

# Set consistent output directory
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/libs/${ANDROID_ABI}")

# FFmpeg configuration and build
add_custom_command(
    OUTPUT "${FFMPEG_OUTPUT}"
    COMMAND ${CMAKE_COMMAND} -E echo "Configuring FFmpeg for ${ANDROID_ABI}..."
    COMMAND "${FFMPEG_SOURCE_DIR}/configure"
        --prefix="${FFMPEG_INSTALL_DIR}"
        --target-os=android
        --arch=${ARCH}
        --disable-everything
        --disable-zlib
        --disable-doc
        --disable-avdevice
        --disable-swscale
        --disable-encoders
        --disable-filters
        --disable-indevs
        --disable-outdevs
        --disable-debug
        --disable-autodetect
        --disable-asm
        --enable-avformat
        --enable-avcodec
        --enable-avutil
        --enable-swresample
        --enable-decoder=h264
        --enable-decoder=aac
        --enable-demuxer=mpegts
        --enable-demuxer=concat
        --enable-muxer=mp4
        --enable-parser=h264
        --enable-parser=aac
        --enable-protocol=file
        --enable-protocol=concat
        --enable-bsf=aac_adtstoasc
        --enable-ffmpeg
        --enable-small
        --extra-cflags=-Os\ -fdata-sections\ -ffunction-sections
        --extra-ldflags=-Wl,--gc-sections\ -s
        --enable-cross-compile
        --cc=${CC}
        --cxx=${CXX}
        --ar=${AR}
        --as=${AS}
        --nm=${NM}
        --ld=${CC}
        --strip="${STRIP}"
        --ranlib="${RANLIB}"
        --sysroot="${SYSROOT}"
    COMMAND make clean
    COMMAND make "-j${NUM_JOBS}"
    COMMAND make install
    WORKING_DIRECTORY "${FFMPEG_BUILD_DIR}"
    COMMENT "Building FFmpeg for ${ANDROID_ABI}"
    VERBATIM
    COMMAND_ECHO STDOUT
)

add_custom_target(build_ffmpeg ALL
    DEPENDS "${FFMPEG_OUTPUT}"
)

# Copy and process libffmpeg.so
set(OUTPUT_LIB_DIR "${CMAKE_BINARY_DIR}/libs/${ANDROID_ABI}")
set(OUTPUT_LIB_PATH "${OUTPUT_LIB_DIR}/libffmpeg.so")
set(UPX_HASH_FILE "${FFMPEG_BUILD_DIR}/libffmpeg.so.upx.hash")
set(FINAL_LIB_DIR "${CMAKE_SOURCE_DIR}/../../main/jniLibs/${ANDROID_ABI}")
set(FINAL_LIB_PATH "${FINAL_LIB_DIR}/libffmpeg.so")

# Copy ffmpeg binary to intermediate location
add_custom_command(
    OUTPUT "${OUTPUT_LIB_PATH}"
    COMMAND ${CMAKE_COMMAND} -E make_directory "${OUTPUT_LIB_DIR}"
    COMMAND ${CMAKE_COMMAND} -E copy "${FFMPEG_OUTPUT}" "${OUTPUT_LIB_PATH}"
    COMMAND "${STRIP}" "${OUTPUT_LIB_PATH}"
    DEPENDS build_ffmpeg
    COMMENT "Copying and stripping ffmpeg to libffmpeg.so"
    VERBATIM
    COMMAND_ECHO STDOUT
)

# Compute hash of libffmpeg.so to track changes
add_custom_command(
    OUTPUT "${UPX_HASH_FILE}"
    COMMAND ${CMAKE_COMMAND} -E sha256sum "${OUTPUT_LIB_PATH}" > "${UPX_HASH_FILE}"
    DEPENDS "${OUTPUT_LIB_PATH}"
    COMMENT "Computing hash of libffmpeg.so"
    VERBATIM
    COMMAND_ECHO STDOUT
)

# UPX compression only if hash changes or UPX not yet applied
if(UPX_AVAILABLE)
    add_custom_command(
        OUTPUT "${OUTPUT_LIB_PATH}.upx"
        COMMAND ${CMAKE_COMMAND} -E make_directory "${FINAL_LIB_DIR}"
        COMMAND ${CMAKE_COMMAND} -E copy "${OUTPUT_LIB_PATH}" "${FINAL_LIB_PATH}"
        COMMAND ${CMAKE_COMMAND} -E echo "Compressing with UPX..."
        COMMAND "${UPX_PATH}" --best "${FINAL_LIB_PATH}" || ${CMAKE_COMMAND} -E true
        COMMAND ${CMAKE_COMMAND} -E touch "${OUTPUT_LIB_PATH}.upx"
        DEPENDS "${OUTPUT_LIB_PATH}" "${UPX_HASH_FILE}"
        COMMENT "Compressing libffmpeg.so with UPX and copying to final location"
        VERBATIM
        COMMAND_ECHO STDOUT
    )
    add_custom_target(upx_ffmpeg ALL DEPENDS "${OUTPUT_LIB_PATH}.upx")
    add_dependencies(upx_ffmpeg copy_ffmpeg)
else()
    add_custom_command(
        OUTPUT "${OUTPUT_LIB_PATH}.upx"
        COMMAND ${CMAKE_COMMAND} -E echo "Skipping UPX compression. UPX not available."
        COMMAND ${CMAKE_COMMAND} -E touch "${OUTPUT_LIB_PATH}.upx"
        COMMAND ${CMAKE_COMMAND} -E make_directory "${FINAL_LIB_DIR}"
        COMMAND ${CMAKE_COMMAND} -E copy "${OUTPUT_LIB_PATH}" "${FINAL_LIB_PATH}"
        DEPENDS "${OUTPUT_LIB_PATH}"
        COMMENT "Copying libffmpeg.so to final location without UPX"
        VERBATIM
        COMMAND_ECHO STDOUT
    )
endif()

add_custom_target(copy_ffmpeg ALL DEPENDS "${OUTPUT_LIB_PATH}" "${OUTPUT_LIB_PATH}.upx")

# Add dummy library to trigger CMake and FFmpeg build
if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/dummy.c")
    add_library(dummy SHARED dummy.c)
    add_dependencies(dummy copy_ffmpeg)
else()
    message(WARNING "dummy.c not found. Creating empty library to trigger FFmpeg build.")
    add_library(dummy SHARED)
    add_dependencies(dummy copy_ffmpeg)
endif()
