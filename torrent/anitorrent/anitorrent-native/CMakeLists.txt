cmake_minimum_required(VERSION 3.21) # Android recommends at least 3.21
#cmake_policy(SET CMP0167 NEW) # crashes android build

project(anitorrent)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_C_STANDARD 17)

include(FetchContent)

# mmap 会导致它安装 signal handler, 而 JVM 依赖 signal handler 才能正常工作.
# 如果 JVM signal handler 被覆盖, JVM 就会因为 SIGBUS/SIGSEGV crash.
add_compile_definitions(TORRENT_HAVE_MMAP=0)
add_compile_definitions(TORRENT_ABI_VERSION=3) # Hide all deprecated APIs

# 安卓需要自己构建所有依赖

# Android-specific settings
if (ANDROID)
    # https://developer.android.com/studio/projects/configure-cmake#call-cmake-cli
    set(ANDROID_STL c++_shared)
    set(CMAKE_SYSTEM_NAME Android)
    set(CMAKE_SYSTEM_VERSION 21) # Adjust as needed
    set(CMAKE_ANDROID_ARCH_ABI arm64-v8a)

    # Set Android NDK paths
    set(ANDROID_NDK ${ANDROID_NDK_HOME})
    set(CMAKE_ANDROID_NDK ${ANDROID_NDK})
    set(CMAKE_TOOLCHAIN_FILE ${ANDROID_NDK}/build/cmake/android.toolchain.cmake)
else ()
	if (APPLE)
		set(BUILD_SHARED_LIBS OFF) # Build libtorrent as a static library
		set(OPENSSL_USE_STATIC_LIBS TRUE) # Statically link OpenSSL
		find_package(OpenSSL REQUIRED)
    endif ()
    
	if (WIN32)
        add_compile_definitions(TORRENT_DISABLE_ENCRYPTION=true)
        add_compile_definitions(TORRENT_USE_OPENSSL)
        find_package(OpenSSL)
    endif ()

    if (WIN32)
        add_compile_definitions(_WIN32_WINNT=0x0A00)
    endif ()

    if (MSVC)
        message(STATUS "MSVC detected")
        add_compile_options(/Zc:__cplusplus)
        set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>DLL")
    endif ()
endif ()

if (NOT DEFINED Boost_INCLUDE_DIR)
    # 不能用这个, 安卓每次更新都会把库全删了, 这库 500MB, 所以我们用 submodule
    #     Fetch and build Boost for Android using CMake
    #    FetchContent_Declare(
    #            boost
    #            GIT_REPOSITORY https://github.com/open-ani/boost.git
    #            GIT_TAG main
    #            UPDATE_DISCONNECTED 1
    #    )
    #    FetchContent_MakeAvailable(boost)
    #    set(Boost_INCLUDE_DIR ${boost_SOURCE_DIR})

    set(Boost_INCLUDE_DIR ${CMAKE_SOURCE_DIR}/libs/boost/)
endif ()

# Fetch and build OpenSSL for Android using CMake
#if (ANDROID)
#    # https://stackoverflow.com/questions/66829315/how-to-use-cmake-fetchcontent-to-link-openssl
#    set(OPENSSL_SOURCE_DIR ${CMAKE_CURRENT_BINARY_DIR}/openssl-src) # default path by CMake
#    set(OPENSSL_INSTALL_DIR ${CMAKE_CURRENT_BINARY_DIR}/openssl)
#    set(OPENSSL_INCLUDE_DIR ${OPENSSL_INSTALL_DIR}/include)
#    set(OPENSSL_CONFIGURE_COMMAND ${OPENSSL_SOURCE_DIR}/config)
#    include(ExternalProject)
#    ExternalProject_Add(
#            OpenSSL
#            SOURCE_DIR ${OPENSSL_SOURCE_DIR}
#            GIT_REPOSITORY https://github.com/openssl/openssl.git
#            GIT_TAG openssl-3.3.1
#            USES_TERMINAL_DOWNLOAD TRUE
#            CONFIGURE_COMMAND
#            ${OPENSSL_CONFIGURE_COMMAND}
#            --prefix=${OPENSSL_INSTALL_DIR}
#            --openssldir=${OPENSSL_INSTALL_DIR}
#            BUILD_COMMAND make
#            TEST_COMMAND ""
#            INSTALL_COMMAND make install
#            INSTALL_DIR ${OPENSSL_INSTALL_DIR}
#    )
#
#    # We cannot use find_library because ExternalProject_Add() is performed at build time.
#    # And to please the property INTERFACE_INCLUDE_DIRECTORIES,
#    # we make the include directory in advance.
#    file(MAKE_DIRECTORY ${OPENSSL_INCLUDE_DIR})
#
#    add_library(OpenSSL::SSL STATIC IMPORTED GLOBAL)
#    set_property(TARGET OpenSSL::SSL PROPERTY IMPORTED_LOCATION ${OPENSSL_INSTALL_DIR}/lib/libssl.${OPENSSL_LIBRARY_SUFFIX})
#    set_property(TARGET OpenSSL::SSL PROPERTY INTERFACE_INCLUDE_DIRECTORIES ${OPENSSL_INCLUDE_DIR})
#    add_dependencies(OpenSSL::SSL OpenSSL)
#
#    add_library(OpenSSL::Crypto STATIC IMPORTED GLOBAL)
#    set_property(TARGET OpenSSL::Crypto PROPERTY IMPORTED_LOCATION ${OPENSSL_INSTALL_DIR}/lib/libcrypto.${OPENSSL_LIBRARY_SUFFIX})
#    set_property(TARGET OpenSSL::Crypto PROPERTY INTERFACE_INCLUDE_DIRECTORIES ${OPENSSL_INCLUDE_DIR})
#    add_dependencies(OpenSSL::Crypto OpenSSL)
#endif ()

# Fetch libtorrent
FetchContent_Declare(
        libtorrent
        GIT_REPOSITORY https://github.com/arvidn/libtorrent.git
        GIT_TAG v2.0.10
)
FetchContent_MakeAvailable(libtorrent)

if (NOT ANDROID)
    # JNI
    set(JAVA_AWT_LIBRARY NotNeeded)
    set(JAVA_JVM_LIBRARY NotNeeded)
#    set(JAVA_INCLUDE_PATH2 NotNeeded)
#    set(JAVA_AWT_INCLUDE_PATH NotNeeded)
    find_package(JNI REQUIRED)
    
    include_directories(${JNI_INCLUDE_DIRS})
endif ()

add_library(anitorrent
        SHARED
        include/anitorrent.hpp
        src/anitorrent.cpp
        gen/cpp/anitorrent_wrap.cpp # 如果你找不到这个文件, 在项目根目录跑一下 ./gradlew build
        src/events.cpp
        include/events.hpp
        src/torrent_info_t.cpp
        include/torrent_info_t.hpp
        src/torrent_add_info_t.cpp
        include/torrent_add_info_t.hpp
        src/session_t.cpp
        include/session_t.hpp
        src/torrent_handle_t.cpp
        include/torrent_handle_t.hpp
        src/global_lock.cpp
        include/global_lock.h
        include/plugin/peer_filter_plugin.h
        src/plugin/peer_filter_plugin.cpp
        include/peer_filter.hpp
        src/peer_filter.cpp
)
target_include_directories(anitorrent PRIVATE include)

# Link libraries
if (ANDROID)
    target_link_libraries(anitorrent PRIVATE torrent-rasterbar android log)
else ()

    # 假设 JNI_INCLUDE_DIRS 是一个包含多个路径的列表
    set(FILTERED_JNI_LIBRARIES "")

    # 遍历 JNI_INCLUDE_DIRS，移除包含 "NotNeeded" 的值
    foreach(dir ${JNI_LIBRARIES})
        if(NOT dir MATCHES "NotNeeded")
            list(APPEND FILTERED_JNI_LIBRARIES ${dir})
        endif()
    endforeach()

    target_link_libraries(anitorrent PRIVATE torrent-rasterbar ${FILTERED_JNI_LIBRARIES})
    
    if (APPLE)
    	target_link_libraries(anitorrent PRIVATE OpenSSL::SSL OpenSSL::Crypto) # Statically link OpenSSL
    endif ()
endif ()
