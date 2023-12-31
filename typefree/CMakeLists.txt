# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html
# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)
#set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -Wall -Werror -Wno-unused-variable")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -O3 -DNDEBUG -Wall -Wno-unused -DHAVE_NEON=1")

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

message(${ANDROID_ABI})

# Oboe
set(OBOE_DIR /home/seni/git/oboe)
add_subdirectory (${OBOE_DIR} ./oboe)
include_directories (${OBOE_DIR}/include)

set (DEBUG_UTILS_PATH "../debug-utils")
include_directories(${DEBUG_UTILS_PATH})

add_library( # Sets the name of the library.
             rec-engine

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             src/main/cpp/jni-bridge.cpp
             src/main/cpp/RecEngine.cpp
             ${DEBUG_UTILS_PATH}/trace.cpp)

add_library(lib_bugsnag SHARED IMPORTED)
set(BUGSNAG_LIB_DIR
        ${CMAKE_SOURCE_DIR}/build/intermediates/bugsnag-libs)
set(BUGSNAG_INCLUDE_DIR ${BUGSNAG_LIB_DIR}/assets/include)
set_target_properties(lib_bugsnag PROPERTIES IMPORTED_LOCATION
        ${BUGSNAG_LIB_DIR}/jni/${ANDROID_ABI}/libbugsnag-ndk.so)
include_directories(${BUGSNAG_INCLUDE_DIR})

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
              log-lib

              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log )

#set(FLATB_DIR /home/seni/git/flatbuffers)
#include_directories(${FLATB_DIR}/include)
#include_directories(${FLATB_DIR}/grpc)
#
#file(GLOB flatb_src "${FLATB_DIR}/src/*.cpp")
#add_library(flatbuffer ${flatb_src})


# tensorflow #
#include_directories(/home/seni/git/tensorflow-android )

#add_library(libtensorflowLite SHARED IMPORTED)
#set_target_properties(libtensorflowLite PROPERTIES IMPORTED_LOCATION /home/seni/AndroidStudioProjects/typefree/typefree/src/main/jniLibs/${ANDROID_ABI}/libtensorflowlite.so)



# ffmpeg
include_directories(/home/seni/androidffmpeg/android/${ANDROID_ABI}/home/seni/androidffmpeg/android/${ANDROID_ABI}/include )


# kaldi
set (KALDIPATH "/home/seni/androidkaldi/${ANDROID_ABI}/kaldi/src")
include_directories(${KALDIPATH})

#link_directories(/home/seni/androidkaldi/android-toolchain/arm-linux-androideabi/lib/armv7-a/)
# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

# openblas
set (BLASPATH "/home/seni/androidkaldi/${ANDROID_ABI}/OpenBLAS/install/")
#if ( ${ANDROID_ABI} STREQUAL arm64-v8a)
#    set (EXTRAF "${BLASPATH}/lib/libopenblas_armv8p-r0.3.7.dev.a")
#else()
#    set (EXTRAF "${BLASPATH}/lib/libopenblas_armv7-r0.3.8.dev.a")
#endif()

# qnnpack
set (QNNPATH "/home/seni/git/QNNPACK/build/android/${ANDROID_ABI}/")

# pytorch
set (TORCHPATH "/home/seni/git/pytorch-android/build_android_${ANDROID_ABI}/")
include_directories(${TORCHPATH}/include)
include_directories(/home/seni/git/pytorch-android /home/seni/git/pytorch-android/aten/src )

include_directories("/home/seni/git/prebuilt-libz/android-${ANDROID_ABI}/include")

include_directories(/home/seni/git/QNNPACK/include /home/seni/git/QNNPACK/deps/clog/include /home/seni/git/QNNPACK/deps/pthreadpool/include /home/seni/git/QNNPACK/deps/cpuinfo/include)

# ARM
#set (CLPATH "/home/seni/git/ComputeLibrary")
#include_directories(${CLPATH})
include_directories("/home/seni/git/XNNPACK/include")
include_directories("/home/seni/git/pthreadpool/include")

target_link_libraries( # Specifies the target library.
                       rec-engine android oboe ${log-lib} lib_bugsnag
                       ${KALDIPATH}/online2/kaldi-online2.a ${KALDIPATH}/base/kaldi-base.a ${KALDIPATH}/util/kaldi-util.a
                       ${KALDIPATH}/lat/kaldi-lat.a ${KALDIPATH}/decoder/kaldi-decoder.a ${KALDIPATH}/matrix/kaldi-matrix.a
                       ${KALDIPATH}/hmm/kaldi-hmm.a ${KALDIPATH}/base/kaldi-base.a ${KALDIPATH}/../../openfst/lib/libfst.a
                       ${KALDIPATH}/nnet3/kaldi-nnet3.a ${KALDIPATH}/cudamatrix/kaldi-cudamatrix.a ${KALDIPATH}/fstext/kaldi-fstext.a
                       ${KALDIPATH}/gmm/kaldi-gmm.a ${KALDIPATH}/tree/kaldi-tree.a ${KALDIPATH}/ivector/kaldi-ivector.a
                       ${KALDIPATH}/feat/kaldi-feat.a ${KALDIPATH}/online2/kaldi-online2.a ${KALDIPATH}/transform/kaldi-transform.a
                       ${KALDIPATH}/rnnlm/kaldi-rnnlm.a ${KALDIPATH}/lm/kaldi-lm.a

                       ${BLASPATH}/lib/libopenblas.a ${BLASPATH}/lib/libclapack.a
                       ${BLASPATH}/lib/libblas.a ${BLASPATH}/lib/liblapack.a
                       ${BLASPATH}/lib/libf2c.a

                        /home/seni/git/prebuilt-libz/android-${ANDROID_ABI}/lib/libz.a

                        /home/seni/androidffmpeg/android/${ANDROID_ABI}/home/seni/androidffmpeg/android/${ANDROID_ABI}/lib/libavutil.a
                        /home/seni/androidffmpeg/android/${ANDROID_ABI}/home/seni/androidffmpeg/android/${ANDROID_ABI}/lib/libavcodec.a
                        /home/seni/androidffmpeg/android/${ANDROID_ABI}/home/seni/androidffmpeg/android/${ANDROID_ABI}/lib/libavformat.a
                        /home/seni/androidffmpeg/android/${ANDROID_ABI}/home/seni/androidffmpeg/android/${ANDROID_ABI}/lib/libswresample.a

        /home/seni/git/XNNPACK/build/android/${ANDROID_ABI}/libXNNPACK.a

                        ${QNNPATH}/libqnnpack.a

        -Wl,--gc-sections -Wl,--whole-archive ${TORCHPATH}/lib/libtorch.a -Wl,--no-whole-archive ${TORCHPATH}/lib/libc10.a
        ${TORCHPATH}/lib/libnnpack.a ${TORCHPATH}/lib/libcpuinfo.a ${TORCHPATH}/lib/libclog.a
        ${TORCHPATH}/lib/libeigen_blas.a ${TORCHPATH}/lib/libpytorch_qnnpack.a /home/seni/git/XNNPACK/build/android/${ANDROID_ABI}/pthreadpool/libpthreadpool.a
                      )