# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)
#set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -Wall -Werror -Wno-unused-variable")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -Wall -Wno-unused")

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

set(OBOE_DIR /home/seni/git/oboe)
add_subdirectory (${OBOE_DIR} ./oboe)
include_directories (${OBOE_DIR}/include)

set (DEBUG_UTILS_PATH "../debug-utils")
include_directories(${DEBUG_UTILS_PATH})

add_subdirectory (/home/seni/git/soxr soxr)
include_directories(/home/seni/git/soxr/src)

add_library( # Sets the name of the library.
             rec-engine

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             src/main/cpp/jni_bridge.cpp
             src/main/cpp/RecEngine.cpp
             ${DEBUG_UTILS_PATH}/trace.cpp)

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

# kaldi
include_directories(/home/seni/androidkaldi/kaldi-android/src)

link_directories(/tmp/android-toolchain/arm-linux-androideabi/lib/armv7-a/)
# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.


target_link_libraries( # Specifies the target library.
                       rec-engine android oboe soxr
                       /home/seni/androidkaldi/kaldi-android/src/online2/kaldi-online2.a /home/seni/androidkaldi/kaldi-android/src/base/kaldi-base.a /home/seni/androidkaldi/kaldi-android/src/util/kaldi-util.a
                       /home/seni/androidkaldi/kaldi-android/src/lat/kaldi-lat.a /home/seni/androidkaldi/kaldi-android/src/decoder/kaldi-decoder.a /home/seni/androidkaldi/kaldi-android/src/matrix/kaldi-matrix.a
                       /home/seni/androidkaldi/kaldi-android/src/hmm/kaldi-hmm.a /home/seni/androidkaldi/kaldi-android/src/base/kaldi-base.a /home/seni/androidkaldi/kaldi-android/tools/openfst-1.6.5/lib/libfst.a
                       /home/seni/androidkaldi/kaldi-android/src/nnet3/kaldi-nnet3.a /home/seni/androidkaldi/kaldi-android/src/cudamatrix/kaldi-cudamatrix.a /home/seni/androidkaldi/kaldi-android/src/fstext/kaldi-fstext.a
                       /home/seni/androidkaldi/kaldi-android/src/gmm/kaldi-gmm.a /home/seni/androidkaldi/kaldi-android/src/tree/kaldi-tree.a /home/seni/androidkaldi/kaldi-android/src/ivector/kaldi-ivector.a
                       /home/seni/androidkaldi/kaldi-android/src/feat/kaldi-feat.a /home/seni/androidkaldi/kaldi-android/src/online2/kaldi-online2.a /home/seni/androidkaldi/kaldi-android/src/transform/kaldi-transform.a
                       #
                       #
                       #
                       #
                       #
                       #
                       # /home/seni/androidkaldi/kaldi-android/src/chain/kaldi-chain.a
                       #/home/seni/androidkaldi/kaldi-android/src/transform/kaldi-transform.a
                       #

                       /home/seni/androidkaldi/OpenBLAS/install/lib/libopenblas_armv7-r0.3.0.dev.a /home/seni/androidkaldi/OpenBLAS/install/lib/libclapack.a
                       /home/seni/androidkaldi/OpenBLAS/install/lib/libblas.a /home/seni/androidkaldi/OpenBLAS/install/lib/liblapack.a
                       /home/seni/androidkaldi/OpenBLAS/install/lib/libf2c.a

                       # Links the target library to the log library
                       # included in the NDK.
                       ${log-lib} )

