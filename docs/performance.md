# 性能量化与效果验收

日期：2026-10-28。当前结论：发现并优化了实验性底栏识别的分配热点，修正了重新绑定的控制流。Android 真机帧率、GPU 成本和视觉效果仍未验证，不能据此宣称“所有应用不卡顿”或“效果完全一致”。

## 可复核的结果

环境：Linux x86-64、Temurin JDK 21.0.13，128 MiB 固定 JVM 堆。测试使用生产 `NavigationProfile` / `GenericTabLocator`，配合仅实现布局查询的 Android View 替身；不模拟 Android 渲染、ART、LSPosed 或 GPU。场景是一棵 613 节点的布局树。优化前源码在本轮修改前保存到 `/workspace/.cloud-tools/perf-baseline`，源码 SHA-256 记录于 [结果 JSON](performance-results.json)。

每个版本使用 3 个独立 JVM。每项先预热 3000 次，随后记录 31 批；表中时间取各 JVM 的批次中位数，再取 3 个 JVM 的中位数。分配由 JDK `ThreadMXBean` 读取当前线程分配量。原始记录在 [performance-runs](performance-runs/)。这些纳秒级数据仅用于同环境代码比较。

| 场景 | 优化前 ns/次 | 优化后 ns/次 | 优化前 B/次 | 优化后 B/次 |
|---|---:|---:|---:|---:|
| 有效标签识别 | 260 | 61 | 1109.3 | 0 |
| 完整 613 节点搜索 | 2184 | 2247 | 4472 | 168 |
| 稳定行缓存命中 | 289 | 40 | 1976 | 0 |
| 模拟 120 Hz 查找，含定期重新校验 | 287 | 43 | 1976 | 7.7 |

完整搜索的 CPU 中位数未改善（约 +2.9%，不能据此承诺搜索加速），分配下降约 96.2%。模拟 120 Hz 的查找分配下降约 99.6%；这里的 120 Hz 指测试时钟推进速率，不是测得手机达到 120 FPS。稳定行缓存场景冻结测试时钟，只测缓存命中，不能替代包含周期校验的场景。批次 p95 见 JSON，不是单次调用的尾延迟。

## 修改与正确性

- 标签集合改为位掩码；精确匹配、重复标签、必需标签和最多一个无标签位置等规则保留，直接比较 `CharSequence`，避免逐帧构造字符串与集合。
- 搜索先检查廉价几何条件，再扫描标签子树；每次搜索复用坐标数组，仍保留深度与访问次数上限。
- 行缓存使用弱键与弱值，每 250 ms 重新检查语义。行隐藏或被移出原底栏时立即重新查找；未命中也短暂缓存，避免逐帧重复全树搜索。标签原地变化最多有一个校验间隔的识别延迟。
- 修正实验性宿主保留原宽度时，重新绑定因宽度结果为 0 而提前退出的问题。未知行隐藏旧液滴，防止灰度标签变更后继续显示旧选中位置。
- 实验性宿主不再投递不会执行页面拉伸的空维护任务。
- `LiquidGlassPanel`、`DropletPanel`、`Backdrop`、`Spring` 与本轮前源码保持一致；未通过降低模糊、折射或动画质量换取上述分配收益。源码未改不等于真机视觉回归已通过。

已执行：47 项宿主/标签检查；100000 组固定种子输入与优化前集合算法的差分比较；18 个布局、缓存及字符序列断言和 1000 次稳定缓存断言；5 项帧数据解析测试；完整 APK 构建、签名和对齐验证；APK 打包检查确认包含本次 DEX、56 个生产类、六款作用域，未包含 JVM 测试替身。

布局夹具覆盖有效底栏、包装容器、重复候选、候选重叠、非覆盖内容、底栏移除/替换/隐藏、标签变化、负缓存过期、时钟回退、搜索预算耗尽和超深树。重新绑定修复目前通过代码检查和 Android 编译；其宿主运行行为仍待设备验证。

```bash
export JAVA_HOME=/workspace/.cloud-tools/jdk-21.0.13+9
export PATH="$JAVA_HOME/bin:$PATH"
bash tests/run-host-profiles.sh
bash tests/run-locator-tests.sh LocatorRegressionTest
bash tests/run-locator-tests.sh
python3 -m unittest discover -s tests -p test_frame_stats.py
./build.sh
python3 tests/verify_apk.py
```

## 真机验收流程（尚未执行）

云端检查到 `adb` 不可用，运行设备采集命令以退出码 2 明确报告未运行。没有可声称通过的 Android 帧数据或效果截图。

在连接并授权 Android 设备、安装测试 APK 后，分别关闭/开启模块，强制停止再打开目标应用，固定设备刷新率、主题、亮度和温度。先预热，再以相同操作录制至少 3 次，比较开关前后的结果；不要混合不同应用版本或不同场景。

以下命令不切换 LSPosed，`--label` 只是人工标记。需先将目标应用置于前台，20 秒内滚动页面、点击底栏，结束时保留底栏可见：

```bash
python3 tests/capture_device_perf.py com.ss.android.ugc.aweme --label disabled
python3 tests/capture_device_perf.py com.ss.android.ugc.aweme --label enabled
```

每次采集将原始 `gfxinfo framestats`、应用版本、Android 版本、截图和 JSON 保存到 `build/device-perf` 下的独立目录。标签不证明模块实际启用，JSON 会明确记录未自动核验 LSPosed 状态；截图需逐张检查，脚本不会自动声称视觉通过。

解析优先使用 Android 的 `FrameDeadline`，不假定 60 Hz。旧 Android 没有有效 deadline 时，应明确提供真实帧预算，例如 `--budget-ms 8.333`（120 Hz）或 `--budget-ms 16.667`（60 Hz）。原始数据也可单独解析：

```bash
python3 tests/frame_stats.py build/device-perf/<采集目录>/framestats.txt
```

默认帧时间门槛是至少一个窗口有 120 个有效帧，且每个窗口 deadline 未命中比例不高于 5%。这是待验证的验收门槛，不是已经达到的成绩。有效帧不足、无数据或缺少 deadline 不会被当作通过。统计包括窗口帧完成延迟，不代表 GPU 单项耗时，也不推算实际屏幕 FPS。返回 0 表示所采集时序数据满足门槛；返回 1 表示未满足；返回 2 表示无有效样本或采集失败。

每款应用还需分别检查浅/深色、所有底栏点击、抖音发布按钮、红点/未读数、详情返回、键盘与全屏视频、前后台、旋转屏幕及灰度底栏更新。视频使用独立 Surface 渲染时，当前 View 背景采集能否包含视频内容必须另行验证，不能用普通页面测试代替。视觉和交互未通过时，即使帧数据通过也不能认定适配完成。

## 0.2.18 合并补充（2026-10-29）

上文微基准只对应当时的 GenericTabLocator / NavigationProfile，不是本版B站 Compose 路径或手机耗电成绩。本版已合并B站专用渲染/语义镜像与生命周期处理，“渲染源码保持不变”只描述原微基准那次改动，不能用于本版。9.17.0的有限视觉验证已观察到胶囊和选中态，未完成9.17性能A/B。此前9.16.1真机A/B/A的前台进程CPU（单核%）为26.56→25.00→28.79，后台为1.34→13.07→5.31，未证实稳定功耗收益。
