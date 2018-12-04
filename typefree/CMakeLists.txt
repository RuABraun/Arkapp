# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)
#set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -Wall -Werror -Wno-unused-variable")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -O3 -DNDEBUG -Wall -Wno-unused")

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

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

set(FLATB_DIR /home/seni/git/flatbuffers)
include_directories(${FLATB_DIR}/include)
include_directories(${FLATB_DIR}/grpc)

file(GLOB flatb_src "${FLATB_DIR}/src/*.cpp")
add_library(flatbuffer ${flatb_src})


# tensorflow #
include_directories(/home/seni/git/tensorflow-android )

add_library(libtensorflowlite SHARED IMPORTED)
set_target_properties(libtensorflowlite PROPERTIES IMPORTED_LOCATION /home/seni/AndroidStudioProjects/typefree/typefree/src/main/jniLibs/armeabi-v7a/libtensorflowlite.so)



# kaldi
set (KALDIPATH "/home/seni/androidkaldi/kaldi/src")
include_directories(${KALDIPATH})

link_directories(/home/seni/androidkaldi/android-toolchain/arm-linux-androideabi/lib/armv7-a/)
# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.


target_link_libraries( # Specifies the target library.
                       rec-engine android oboe libtensorflowlite flatbuffer
                       ${KALDIPATH}/online2/kaldi-online2.a ${KALDIPATH}/base/kaldi-base.a ${KALDIPATH}/util/kaldi-util.a
                       ${KALDIPATH}/lat/kaldi-lat.a ${KALDIPATH}/decoder/kaldi-decoder.a ${KALDIPATH}/matrix/kaldi-matrix.a
                       ${KALDIPATH}/hmm/kaldi-hmm.a ${KALDIPATH}/base/kaldi-base.a ${KALDIPATH}/../tools/openfst/lib/libfst.a
                       ${KALDIPATH}/nnet3/kaldi-nnet3.a ${KALDIPATH}/cudamatrix/kaldi-cudamatrix.a ${KALDIPATH}/fstext/kaldi-fstext.a
                       ${KALDIPATH}/gmm/kaldi-gmm.a ${KALDIPATH}/tree/kaldi-tree.a ${KALDIPATH}/ivector/kaldi-ivector.a
                       ${KALDIPATH}/feat/kaldi-feat.a ${KALDIPATH}/online2/kaldi-online2.a ${KALDIPATH}/transform/kaldi-transform.a
                       ${KALDIPATH}/rnnlm/kaldi-rnnlm.a ${KALDIPATH}/lm/kaldi-lm.a

                       /home/seni/androidkaldi/OpenBLAS/install/lib/libopenblas_armv7-r0.3.0.dev.a /home/seni/androidkaldi/OpenBLAS/install/lib/libclapack.a
                       /home/seni/androidkaldi/OpenBLAS/install/lib/libblas.a /home/seni/androidkaldi/OpenBLAS/install/lib/liblapack.a
                       /home/seni/androidkaldi/OpenBLAS/install/lib/libf2c.a

                       # Links the target library to the log library
                       # included in the NDK.
                       ${log-lib} )

