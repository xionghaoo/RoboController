
# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.18.1)

set(LOCAL_CERTIFICATE platform)

# Declares and names the project.

project("robocontroller")
#set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=gnu++11")

set(OPENCV_DIR ${CMAKE_SOURCE_DIR}/../../../opencv)
set(THIRD_LIBS ${CMAKE_SOURCE_DIR}/../jniLibs)
set(libname robocontroller)
include_directories(
        ${CMAKE_SOURCE_DIR}/include
        ${OPENCV_DIR}/include
)
link_directories(${THIRD_LIBS}/${ANDROID_ABI})

file(GLOB src-files
        ${CMAKE_SOURCE_DIR}/*.cpp)

add_library( # Sets the name of the library.
        ${libname}
        # Sets the library as a shared library.
        SHARED
        # Provides a relative path to your source file(s).
        ${src-files} )

# opencv
add_library(opencv_java3 SHARED IMPORTED)
set_target_properties(
        opencv_java3
        PROPERTIES
        IMPORTED_LOCATION
        "${OPENCV_DIR}/libs/${ANDROID_ABI}/libopencv_java3.so"
)

# third library
#add_library(apple SHARED IMPORTED)
#set_target_properties(
#        apple
#        PROPERTIES
#        IMPORTED_LOCATION
#        ${THIRD_LIBS}/${ANDROID_ABI}/libapple.so
#)

find_library( # Sets the name of the path variable.
        log-lib
        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log )
find_library(jnigraphics-lib jnigraphics)
find_library(android-lib android)

set(third-party-libs TouchScreenCore)

target_link_libraries(
        # Specifies the target library.
        ${libname}
        #        apple
        # opencv_imgproc opencv_core opencv_dnn opencv_dnn tegra_hal tbb cpufeatures
        # Links the target library to the log library
        # included in the NDK.
        ${log-lib}
        ${jnigraphics-lib}
        ${third-party-libs}
        ${android-lib}
        opencv_java3
)

