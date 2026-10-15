# BiliAudioPlayer

一个将 B 站收藏夹作为歌单的原生 Android 音频播放器。支持 Android 8.0 及以上。

当前版本为 **0.1.2**，历史版本为0.1.0 / 0.1.1；功能范围见《b站分p播放器编写计划.md》。

## 使用

1. 安装 `artifacts/BiliAudioPlayer-0.1.2-debug.apk`，打开「BiliAudioPlayer」。可直接覆盖旧版安装，保留登录与本地置顶。自行构建的文件位于 `app/build/outputs/apk/debug/app-debug.apk`。
2. 默认尝试打开本机哔哩哔哩 App 确认授权；未安装或不适配时可选择扫码登录。同一台手机可以先截图，再在 B 站「扫一扫」中从相册选择。确认后自动同步。
3. 首页显示自己创建的收藏夹。点击收藏夹查看视频；多 P 视频进入分 P 列表，单 P 视频直接播放。
4. 选择曲目后，所属收藏夹按视频原序、分 P 原序组成队列。无法读取的视频不进入队列，所选曲目无法读取时显示错误。大收藏夹首次展开需要等待，可取消。
5. 播放器提供进度跳转、暂停、上一曲 / 下一曲、顺序 / 列表循环 / 单曲循环 / 随机模式、队列点播，支持后台与系统媒体通知控制。
6. 收藏夹通过图钉置顶；稿件和分P通过三点菜单置顶。设置按账号保存在本机，只改变浏览顺序。退出登录会清除登录会话并停止播放，保留该账号的置顶偏好。
7. 视频和分 P 列表右侧拖动条可快速跳转。首页齿轮进入设置页，当前设置内容留空。
8. 稿件和分 P 可长按查看完整标题；右侧三点菜单包含置顶、复制源链接、跳转 B 站 App。
9. 首页直接浏览收藏夹。进入收藏夹后，搜索栏仅筛选当前收藏夹的稿件标题；进入分P列表后，仅筛选当前稿件的分P标题。支持忽略英文大小写的关键词匹配，清空恢复完整列表。搜索不匹配UP主、BV号或所属合集，也不改变播放队列。

0.1.1 播放页面会按屏幕剩余高度先缩小间距，再缩小封面；极端小窗口或大字体仍可滚动。

本机 App 授权与二维码授权两个入口均已由用户在当前手机实测成功，确认后可登录并同步收藏夹。两个入口共用同一次登录会话。

## 构建

需要 JDK 17 或 21、Android SDK 35。依赖版本已固定；Windows 脚本把工具和缓存存到项目 `.tools`，不修改全局环境。

```powershell
# 首次准备；已有 Android SDK 可直接用 Android Studio 打开项目
.\scripts\setup-android.ps1 -AcceptLicenses

# 构建 APK，并执行现有单元测试与静态检查
.\scripts\build.ps1

# 只构建 APK
.\scripts\build.ps1 -Tasks assembleDebug
```

Android Studio 或已有环境也可以使用 `gradlew.bat assembleDebug`（macOS/Linux：`./gradlew assembleDebug`）。本机工具路径写在被忽略的 `local.properties`。调试密钥保存在 `.tools/debug.keystore`，发布签名需自行配置。

## 关键验证

默认构建检查接口解析、分页、队列原序、取消行为与 Android lint。设备测试包含关键页面操作、置顶和加密存储。公开音轨集成测试需显式启用联网：

```powershell
.\scripts\build.ps1 -Tasks @('connectedDebugAndroidTest', '-Pandroid.testInstrumentationRunnerArguments.liveNetwork=true')
```

测试设备应是专用模拟器或测试手机。实际账号的扫码授权、私有收藏夹访问及不同手机的后台限制，需要最终在自己的设备上确认。详细实际执行结果见《施工调试log.md》，不要把测试代码存在视为验证通过。

2026-10-15 用户已确认交付 APK 可扫码登录并显示自己的收藏夹；API35 模拟器已完成11项关键设备验证，含实际分 P 音轨播放、进度跳转、自动切歌、后台播放、媒体通知和置顶保存。24项单元测试、APK构建和lint通过。详细过程见《施工调试log.md》。

## 目录

```text
app/src/main/java/io/github/biliaudioplayer/
  data/       B 站接口、分页、扫码与加密会话
  domain/     收藏夹展开为分 P 队列
  model/      数据与界面状态
  playback/   Media3 后台服务、音频源、播放控制
  store/      本地置顶
  ui/         Compose 界面
  AppViewModel.kt / MainActivity.kt / BiliApplication.kt
scripts/      工具链与构建
docs/         接口核对
更新计划.md   当前范围、进度、后续工作
施工调试log.md 实际改动、失败与验证证据
```

使用真实 B 站接口，无演示收藏夹。接口可能受平台调整、登录状态或内容权限影响；失败时会显示提示。当前使用独立 AAC 音轨，不支持只有混合音视频资源的内容。账号 Cookie 通过 Android Keystore 加密，仅保存在设备；音频地址按播放需要重新解析。
