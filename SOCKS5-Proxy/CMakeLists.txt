cmake_minimum_required(VERSION 3.0)

project(SOCKS5-Proxy)

find_package(c-ares REQUIRED)
if(NOT CARES_FOUND)
  message(SEND_ERROR "c-ares not found.")
  return()
else()
  include_directories(${CARES_INCLUDE_DIR})
endif()

add_definitions(-std=c++14)

include_directories(.)

set(TARGET_SOURCES
  main.cpp
  forward-handler.cpp
  proxy-state.cpp
  socks-handler.cpp
)

add_executable(SOCKS5-Proxy ${TARGET_SOURCES})

target_link_libraries(SOCKS5-Proxy ${CARES_LIBRARIES})

