# 不规则图形面积估测器 v12.1（课堂试验版）

试用地址：https://sevendamon.github.io/irregular-area-estimator/

## 使用

1. 用安卓手机或平板的系统浏览器打开部署后的 HTTPS 网址。直接点击 `index.html` 得到的 `file://` / `content://` 地址无法加载自动识别模型。
2. 选择“手”，拍照或从相册选图。把一张平整的 **A4 纸（21 × 29.7 cm）** 完整放进画面，手掌平放在纸上，五指自然张开；仅前臂可从纸边伸出。
3. 网页给出纸张四角候选点。老师检查并拖动四角到真实纸角后点“完成”。如果纸角被袖子、手或桌面遮住，应重拍；凭猜测补角会直接影响 cm² 标定。
4. 稍等模型自动描出手形并截到腕部。先看深蓝轮廓是否贴合五指、掌边和腕部，再确认结果。主数字是教材的“满格 + 不满格 × 0.5”估算，不是实物真值。可切换 1 / 2 / 3 cm 格子做课堂比较。
5. “边缘上色”可切换“斜线区分”和“同色填充”。两种样式都只在手形轮廓内上色；同色填充会让边缘也呈橙色，但不满格仍按 0.5 格估算。需要看哪些格被判为不满格，可用“只看 → 不满格”。

“树叶／纸片”沿用旧版色差算法，尚未达到手形模式的验证程度。内置示例用这个模式演示数格子。

## 离线与隐私

- 照片在当前浏览器内处理；本项目代码没有上传照片的接口。
- 第一次打开需要下载约 50 MB 的网页、运行库和模型。首次完整加载后，支持从浏览器缓存离线重开；浏览器清理站点数据或系统回收缓存后，需要重新联网下载。离线重开已在桌面 Chrome 模拟断网测试通过，安卓真机仍需验证。
- 缓存的离线功能要求 HTTPS 站点或电脑本机 `localhost`。局域网 `http://电脑IP` 可以用于同网段临时联机测试，但浏览器通常不会安装离线缓存。

## 目前确认的边界

- 已用用户提供的三张同一只手的实拍照检查轮廓，其中棋盘桌布照片可保留五指；尚无其他肤色、设备、光照和手型的系统测试，更没有独立人工标注的面积真值。
- 新照片右下纸角被袖子挡住。用估计角点算出的面积只验证流程，不验证数值准确度。
- 细手指、运动模糊、手套、强阴影、遮挡和纸面外的手部都可能导致识别失败。失败时不应把面积数字作为正确结果。
- 腕点来自手部关键点模型，因此“手掌面积”的边界是模型给出的腕点截线，并非统一的解剖学测量标准。课堂比较时应固定拍摄姿态与腕部边界。

## 开发者本地运行

在本目录运行 `python -m http.server 8765`，然后访问 `http://localhost:8765/`。不要直接双击 HTML。网页没有构建步骤；部署时需把本目录所有文件与 `wasm/` 子目录一同复制。

## 第三方组件

- `vision_bundle.mjs` 和 `wasm/`：`@mediapipe/tasks-vision` 0.10.35，Apache-2.0，https://www.npmjs.com/package/@mediapipe/tasks-vision
- `hand_landmarker.task`：Google MediaPipe Hand Landmarker (Full)，模型卡 https://storage.googleapis.com/mediapipe-assets/Model%20Card%20Hand%20Tracking%20(Lite_Full)%20with%20Fairness%20Oct%202021.pdf
- `magic_touch.tflite`：Google MediaPipe MagicTouch，模型卡 https://storage.googleapis.com/mediapipe-assets/Model%20Card%20MagicTouch.pdf
- Apache 2.0 正文见 `THIRD_PARTY_APACHE_2_LICENSE.txt`。
