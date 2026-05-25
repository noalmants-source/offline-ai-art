cmake_minimum_required(VERSION 3.22.1)
project("offlineai")

set(CMAKE_CXX_STANDARD 17)

# Definir Vulkan si está activado
if (SD_VULKAN)
    add_compile_definitions(SD_USE_VULKAN)
endif()

# Incluir el subdirectorio de stable-diffusion.cpp
add_subdirectory(stable-diffusion.cpp)

# Nuestra librería puente JNI
add_library(${CMAKE_PROJECT_NAME} SHARED native-lib.cpp)

# Habilitar los logs de Android en nuestro C++
find_library(log-lib log)

# Incluir las cabeceras de stable-diffusion.cpp
target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE stable-diffusion.cpp)

# Enlazar las librerías
target_link_libraries(${CMAKE_PROJECT_NAME}
    stable-diffusion
    ${log-lib}
)
