# 已停用图片监听器源码归档（v0.1.29）

本目录是 v0.1.30 安全隔离前，从 v0.1.29 原样复制的回滚材料，不属于 Android app 编译源集。

归档内容：
- `app/src/main/java/com/toimg/app/core/util/NewImageWatcher.kt`
- `app/src/main/java/com/toimg/app/core/work/NewImageWatchWorker.kt`
- `app/src/main/java/com/toimg/app/core/util/NewImageIngestor.kt`

`SHA256SUMS.baseline` 记录隔离前源文件哈希。恢复时必须先审查用户授权范围、启动入口和后台任务策略；仅复制回源码目录不会自动恢复监听，因为本版本已无生产调用方。

注意：通用 `ContentResolver` 仍用于 SAF、MediaStore、Photo Picker、图片读写、移动和撤回等正常功能；本次只隔离自动监听链路。
