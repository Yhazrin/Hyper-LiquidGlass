# 0.2.17-candidate.2：底栏调度优化与证据边界

这是候选源码，不是已完成真机验收的稳定版本。基线为 `v0.2.16-beta.1`（源码提交 `1ef5502a331a13f5e79dc87bbcdc5e85f6ec8adc`）。

## 修改
- B站首次 Compose 绘制前准备版本开关；找不到装扮图片 Hook 时不启用玻璃，保留原生导航。
- 延迟镜像回调直接读取语义，不再无条件请求父视图重绘。
- 语义树未就绪时也执行 64ms 节流；原先 `state.ready` 条件导致此路径每帧读取。
- 缓存 `getSemanticsOwner` 反射 Method，具体 Compose 类变化时重新绑定；不缓存 owner 或语义树，避免陈旧选中状态。
- 同一布局失败的自主定时重试最多 8 次，继续等待原生变化。
- 首页暂停取消镜像、安装重试、页面修复、主题探测、选择 settle 和动画回调；绘制前检查生命周期、窗口可见性。
- `GlassMetrics` 在离开首页时输出一次局部绘制/语义读取耗时和动画帧计数，不逐帧写日志。

## 已执行的量化验证
执行真实编译后的 `BiliTabMirror.class`，并不是把节流条件抄一遍测试。
环境是 JVM 最小 UI fixture、虚拟时钟和固定输入，`getSemanticsOwner()` 返回 null。
fixture 替代 Android View/Handler 和生命周期依赖，不包含真实 Compose、渲染线程或 GPU。

| 60秒虚拟窗口（另排空100ms尾回调） | 0.2.16基线 | candidate.1 | candidate.2 | 对基线减少 |
|---|---:|---:|---:|---:|
| 60Hz、3600次 sync：owner读取 | 3600 | 3600 | 939 | 73.9% |
| 120Hz、7200次 sync：owner读取 | 7200 | 7200 | 939 | 87.0% |
| 单次延迟刷新：强制 invalidate | 1 | 0 | 0 | 1次 |
| 暂停时已有待执行刷新：强制 invalidate | 1 | 0 | 0 | 1次 |

这些数字只说明固定输入下的读取/调度次数降低，**不等于 CPU、GPU 或耗电下降这些百分比**。
空 owner fixture 的单次读取很轻，不代表真实语义树遍历的时间。未计时，不把 JVM 微秒数字外推到 Android。
正常语义树、拖动、主题/装扮和折叠屏效果不在这个 fixture 中。
暂停检查只调用真实 `BiliTabMirror.suspend`，并模拟 lifecycle gate；不能证明整个 Android 生命周期 Hook 已正确触发。

## 复现
使用 Java 11+，先正常构建，保证 `build/classes` 与待测源码一致，然后运行：

```sh
python3 checks/check-mirror-scheduling.py   build/classes   "$TOOL_ROOT/plat/android-34/android.jar"   /tmp/liquidglass-mirror-check
```

脚本保存字面 stdout/stderr、退出状态和命令到输出目录 `LEDGER.json`。它只创建输出目录，不修改输入 classes。
比较旧版时，应在独立目录编译 `v0.2.16-beta.1`，然后对其 `build/classes` 运行相同脚本和输入。
基线、候选、回滚源均编译成功；回滚后原文件哈希一致；diff 可重建候选。

## 真实功耗尚无 A/B 数据
尚未安装候选或完成新旧版同条件真机测试，不承诺双底栏问题全部消失或电池续航增加。
本轮不再盲目下调模糊质量、采样分辨率或拖动帧率。下一步应先用同一设备、页面、亮度、刷新率、网络、温度及相同持续时间比较静止/滚动/后台。

日志 `glass_metrics` 中：
- `active_ms` 是首页活动时间；`animation_frames` 为液滴帧回调次数。
- `panel_draws`/`panel_record_ns` 只覆盖独立玻璃面板的同步绘制记录，不包括异步 GPU 和液滴内嵌绘制。
- `semantics_reads`/`semantics_ns` 为镜像语义读取次数及同步耗时。
- 次数/active_ms×1000 可得每秒次数，ns/次数/1e6 为每次平均毫秒；次数为0时不计算平均值。
- 暂停时输出日志不能单独证明暂停后零回调，需配合系统跟踪。

本地候选 APK 为临时 debug 签名，不能覆盖公开签名版本。正式发布前需要原发布签名和真机验收；本次不发布 Release APK。
