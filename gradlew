#!/bin/sh
# Gradle Wrapper 启动脚本
# 如果没有 gradle-wrapper.jar，这个脚本会提示用 gradle 直接编译

DIR="$(cd "$(dirname "$0")" && pwd)"
APP_HOME="$DIR"

if [ -f "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" ]; then
    # 有 wrapper jar，用 wrapper 方式运行
    CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
    exec java -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
else
    # 没有 wrapper jar，检查系统是否有 gradle
    if command -v gradle >/dev/null 2>&1; then
        exec gradle "$@"
    else
        echo "错误：找不到 gradle-wrapper.jar，系统也未安装 gradle。"
        echo "请在 Android Studio 中打开项目让其自动生成，或运行: gradle wrapper"
        exit 1
    fi
fi
