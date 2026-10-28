# 宿主调研与证据

调研日期：2026-10-28。本文区分「其他项目的记录」「本模块的静态检查」和「本模块真机验证」，不将其混为兼容性结论。未获取官方 APK 的版本号不猜测，也不将模块版本号当作应用版本号。

## 抖音

来源：[DouyinEnhancer 中文说明](https://github.com/twyora/DouyinEnhancer/blob/ee5eec15efdc288d22d2b07f1ce4ef41c2882ce0/README_zh_CN.md)。该快照列出抖音 37.6.0、38.8.0，并将 40.3.0 标为未经维护者测试。这是该项目的测试环境记录，不是抖音当前最新版本声明，更不是本模块测试记录。

[DouyinPackage.kt](https://github.com/twyora/DouyinEnhancer/blob/ee5eec15efdc288d22d2b07f1ce4ef41c2882ce0/app/src/main/java/io/github/twyora/douyinenhancer/hook/DouyinPackage.kt) 明确引用 `com.ss.android.ugc.aweme.main.MainActivity`，并分别定义主页、商城、朋友、发布、消息、个人页等底栏业务标识。[底栏处理代码](https://github.com/twyora/DouyinEnhancer/blob/ee5eec15efdc288d22d2b07f1ce4ef41c2882ce0/app/src/main/java/io/github/twyora/douyinenhancer/hook/ui/HomeBottomTabHooker.kt) 同时处理服务端及默认底栏配置。

据此将本模块的抖音安装入口限制到该主页 Activity，保留按运行时标签与几何识别的路径。业务标识不等于 Android view resource ID，不将这些字符串直接用作资源 ID，也不照搬其他模块的混淆方法名。发布入口仍保留原生触摸处理。

## 哔哩哔哩

来源：[BiliRoamingX MainActivityPatch.kt](https://github.com/BiliRoamingX/BiliRoamingX/blob/ae58109f3acdd53ec2d2b3fb439c2a2ef1886221/patches/src/main/kotlin/app/revanced/patches/bilibili/misc/integrations/patch/MainActivityPatch.kt)。该快照明确操作 `tv.danmaku.bili.MainActivityV2`。

据此限制本模块的哔哩哔哩安装入口。该信息不能证明底栏 view 类型、布局结构或任一版本的本模块适配效果。

## 美团、美团外卖

已经尝试应用官网及应用宝详情页：

- https://www.meituan.com/mobile/
- https://waimai.meituan.com/
- https://sj.qq.com/appdetail/com.sankuai.meituan
- https://sj.qq.com/appdetail/com.sankuai.meituan.takeoutnew

当前环境代理对上述站点返回 `Tunnel connection failed: 403 Forbidden`。抖音官网、应用宝及哔哩哔哩官方下载页也同样受限。这是环境访问限制，不能据此推断站点不存在或应用版本。

GitHub 搜索结果中的示例仿制项目不作为官方应用的布局证据。尚无可靠证据用于指定美团两款的主页/底栏类名，因此仍采用严格的实验性标签与布局识别，不填写未经核实的类名。

已在环境配置草稿中添加 `www.douyin.com`、`www.meituan.com`、`waimai.meituan.com`、`sj.qq.com`、`app.bilibili.com` 五个域名；草稿保存不等于运行时网络已经放通。需要在环境设置保存/应用网络配置后，再验证访问并取得官方版本信息。如下载跳转到其他域名，应按实际跳转记录补充准确域名。

## 当前验证边界

- 可执行：纯 JDK 的宿主隔离/标签识别回归检查，完整 Android 编译、DEX、打包、APK 签名与对齐验证。
- 未执行：新增应用的官方 APK 静态分析、Android/LSPosed 真机运行与视觉验证。
- 本模块新增四款宿主均保持「实验性、未完成真机验证」状态；公开项目的版本记录不会自动升级此状态。
