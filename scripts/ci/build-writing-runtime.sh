#!/usr/bin/env bash
set -euo pipefail
revision=74d4f5b041ad837153b0e90fc864b8290e01d8d5
source_dir="$RUNNER_TEMP/writing-source"
if ! test -f "$source_dir/include/llama.h"; then
  git init -q "$source_dir"
  git -C "$source_dir" remote add origin https://github.com/ggml-org/llama.cpp.git
  git -C "$source_dir" sparse-checkout init --no-cone
  git -C "$source_dir" sparse-checkout set /CMakeLists.txt /LICENSE /cmake/ /src/ /include/ /ggml/
  git -C "$source_dir" fetch --quiet --filter=blob:none --depth=1 origin "$revision"
  git -C "$source_dir" checkout --quiet --detach FETCH_HEAD
fi
test "$(git -C "$source_dir" rev-parse HEAD)" = "$revision"
mkdir -p app/src/main/assets/writing-licenses
cp "$source_dir/LICENSE" app/src/main/assets/writing-licenses/llama.cpp-MIT.txt
curl --fail --location --retry 2 --output app/src/main/assets/writing-licenses/Qwen-Apache-2.0.txt \
  https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/raw/9217f5db79a29953eb74d5343926648285ec7e67/LICENSE
cmake -S native-writing -B "$RUNNER_TEMP/writing-host" -DCMAKE_BUILD_TYPE=Release -DLLAMA_SOURCE_DIR="$source_dir" -DLLAMA_BUILD_COMMON=OFF
cmake --build "$RUNNER_TEMP/writing-host" --target writing-eval -j2
# Synthetic linguistic evaluation needs only the existing host decoder, never Android compilation.
if test "${WRITING_HOST_ONLY:-0}" = 1; then
  exit 0
fi
sdkmanager --install 'cmake;3.22.1' 'ndk;28.0.13004108' >/dev/null
"$ANDROID_HOME/cmake/3.22.1/bin/cmake" -S native-writing -B "$RUNNER_TEMP/writing-arm64" \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_HOME/ndk/28.0.13004108/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-24 -DANDROID_STL=c++_static \
  -DCMAKE_BUILD_TYPE=Release -DLLAMA_SOURCE_DIR="$source_dir" -DLLAMA_BUILD_COMMON=OFF
"$ANDROID_HOME/cmake/3.22.1/bin/cmake" --build "$RUNNER_TEMP/writing-arm64" --target heliboard_writing -j2
mkdir -p app/src/main/jniLibs/arm64-v8a
cp "$RUNNER_TEMP/writing-arm64/libheliboard_writing.so" app/src/main/jniLibs/arm64-v8a/
test -s app/src/main/jniLibs/arm64-v8a/libheliboard_writing.so
