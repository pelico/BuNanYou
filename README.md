# AICompose · AI 构图助手

端侧 AI 拍照助手：**相册照片重构** + **实时取景构图指导**。无需联网、无需 API Key，所有推理都在手机上完成，离线可用。

![platform](https://img.shields.io/badge/Platform-Android%208.0%2B-brightgreen)
![build](https://img.shields.io/badge/Architecture-arm64--v8a-blue)
![version](https://img.shields.io/badge/Version-0.4.0-orange)

---

## 功能

### 📸 相册重构

选一张照片，AI 分析构图后给出最佳裁剪框，并直接展示重构结果。

- 基于 **GAIC（Grid-Anchor-based Image Cropping）ONNX 模型**，端侧 CPU 推理
- 在原图上叠加虚线裁剪框，预览重构后的画面
- 显示构图评分与推理耗时

### 🎬 实时提示（拍照姿势指导）

打开相机对准场景，App 实时识别环境并推荐匹配的拍照姿势，半透明人物剪影直接叠加在取景框上，照着摆即可。

- **端侧场景识别**：ML Kit 图像标签，离线运行
  - 支持 6 类场景：海边 🌊 / 街道 🏙️ / 咖啡厅 ☕ / 公园绿地 🌳 / 雪山高原 🏔️ / 城市夜景 🌃
  - 模糊匹配 + 多帧投票去抖，识别结果稳定不闪烁
  - 取景框实时显示命中标签与置信度（如 `🌊 海边 · sea 87%`）
- **场景匹配姿势库**：内置 30 个姿势模板（每场景 5 个），识别到新场景自动推荐
- **剪影叠加引导**：人物剪影半透明叠在取景框上，对齐即可拍出模板同款
- 也可手动切换场景与姿势，姿势卡片带 "AI 推荐" 角标

## 技术栈

| 模块 | 技术 |
| --- | --- |
| UI | Kotlin + Jetpack Compose + Material 3 |
| 相机 | CameraX（取景、ImageAnalysis） |
| 场景识别 | ML Kit Image Labeling（端侧、离线） |
| 构图分析 | ONNX Runtime Mobile + GAIC 模型 |
| 图片加载 | Coil |
| 导航 | Navigation Compose |
| CI | GitHub Actions（自动构建签名 APK + Release） |

## 项目结构

```
app/src/main/java/com/aicompose/
├── MainActivity.kt              # 底部导航 (相册重构 / 实时提示)
├── core/vision/
│   ├── CompositionAnalyzer.kt   # 分析器接口 (本地/云端可切换)
│   ├── AnalyzerHolder.kt        # 分析器单例持有者
│   ├── GaicOnnxAnalyzer.kt      # GAIC ONNX 本地构图分析
│   ├── ImagePreprocessor.kt     # 图片预处理 + 裁剪候选框生成
│   ├── CloudCompositionAnalyzer.kt # 云端 VLM 占位 (预留)
│   └── scene/
│       ├── SceneCatalog.kt      # 场景定义 + 30 个姿势模板库
│       └── SceneDetector.kt     # ML Kit 场景识别 (模糊匹配 + 多帧投票)
├── feature/
│   ├── gallery/GalleryScreen.kt # 相册重构页
│   └── camera/
│       ├── CameraScreen.kt      # 实时取景页
│       ├── SilhouetteOverlay.kt # 剪影叠加 + 三分线
│       └── PoseCards.kt         # 姿势卡片列表 (AI 推荐角标)
└── ui/theme/                    # 主题

app/src/main/assets/
├── gaic.onnx                     # GAIC 构图模型 (~12MB)
└── poses/                        # 姿势素材: 剪影 PNG + 参考图 JPG
```

## 构建

### 本地构建

需要 JDK 17 + Android SDK 34：

```bash
./gradlew assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`

### 自动构建（GitHub Actions）

推送 `main` 分支即触发 [build.yml](.github/workflows/build.yml)：

- 自动读取 `versionName` 打 tag（如 `v0.4.0`）并发布 GitHub Release
- 固定 release 签名，新版本可直接**覆盖安装升级**（无需卸载重装）
- 仅打包 `arm64-v8a` 架构，控制 APK 体积

## 签名

- 签名文件：`keystore/aicompose-release.jks`（已提交仓库）
- 密码/别名均为 `aicompose`（仅用于个人分发场景）
- debug 与 release 共用同一签名，便于互相覆盖升级

## 姿势素材

姿势剪影与参考图由 [pose-compare](../pose-compare) 下的脚本生成：

- `tools/gen_pose_templates.py`：根据 COCO 关键点模板渲染新姿势素材
- `tools/cut_silhouette.py`：rembg 从 AI 参考图提取人物轮廓
- `tools/render_poses.py`：矢量线条姿势插画

## 已知限制 / 后续规划

- 云端 VLM 构图分析为占位实现，`AnalyzerHolder` 可一键切换
- 场景识别使用 ML Kit 通用标签模型，复杂混合场景可能误判，可通过扩充
  `SceneCatalog.kt` 的关键词表持续提升
- 姿势库当前 30 个，可通过新增模板脚本持续扩充

## License

仅用于个人学习交流。
