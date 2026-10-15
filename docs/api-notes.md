# B站接口核对记录

核对日期：2026-10-14（Asia/Shanghai）。以下是当前网络下的只读探测结果，不代表官方稳定性承诺。没有使用任何真实账号 Cookie，也没有执行收藏、点赞或账号设置写入。

## 最小调用链

1. 二维码生成 → 轮询确认 → 收集本次登录响应的 Cookie → `nav` 验证 → 加密保存。
2. `nav.data.mid` → 收藏夹分页 → 收藏内容分页 → 视频 `pages` → 分 P 的 `cid`。
3. 播放时按 `bvid + cid` 重新取独立 AAC 音轨地址；播放源失效后按稳定 ID 再解析。

`bvid` 是视频稿件的 BV 号，`cid` 是具体分 P 的内容 ID，`mid` 是用户 ID。三者不能互换。收藏夹内容接口的 `media_id` 应传目录返回的完整 `id`，不要传 `fid`。

## 端点与实际探测

所有端点均使用 HTTPS、非空 User-Agent 和 `Referer: https://www.bilibili.com/`。

| 用途 | GET 端点 | 字段及结果 |
| --- | --- | --- |
| 二维码生成 | `https://passport.bilibili.com/x/passport-login/web/qrcode/generate` | 实测 `code=0`，`data.url`、`data.qrcode_key`；本次 URL 主机是 **account.bilibili.com**，不能硬编码旧扫码页面域名 |
| 二维码轮询 | `https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key=…` | 实测未扫码时外层 `code=0`、`data.code=86101`；`data` 另含 url / refresh_token / timestamp / message |
| 当前账号 | `https://api.bilibili.com/x/web-interface/nav` | 未登录实测 `code=-101`、`data.isLogin=false`；登录后读取 mid / uname / face |
| 收藏夹分页 | `https://api.bilibili.com/x/v3/fav/folder/created/list?up_mid=686127&pn=1&ps=20` | 实测 `data.count=39`，第一页20夹，`has_more=true`；目录每项含 id/title/cover/media_count |
| 收藏内容分页 | `https://api.bilibili.com/x/v3/fav/resource/list?media_id=1052622027&pn=1&ps=2&platform=web&order=mtime` | 实测 `data.info.media_count=28`、2条 medias、`has_more=true`；视频 type=2，bvid/bv_id、title、upper.name、duration、attr |
| 视频及分 P | `https://api.bilibili.com/x/web-interface/view?bvid=BV1xx411c7mD` | 实测 `pages` 含 cid=62131、page、part、duration；同时含 title/pic/owner |
| 单独分 P 列表 | `https://api.bilibili.com/x/player/pagelist?bvid=BV1xx411c7mD` | 实测与 view.pages 相同；实现使用 view 减少请求 |
| 音轨解析 | `https://api.bilibili.com/x/player/playurl?bvid=BV1xx411c7mD&cid=62131&fnval=16&fnver=0&qn=64` | 实测 dash.audio 有 AAC `mp4a.40.2`、30216/30232、baseUrl/backupUrl/bandwidth；无需登录即可获得此次测试内容的音轨 |

同参数的 `/x/player/wbi/playurl` 在本次网络中也无签名成功。实现先使用实测可用的 `/x/player/playurl`；如果平台以后要求签名，应显式实现并验证 WBI，不应无上限重试或声称所有账号/地区均可用。

`folder/created/list-all` 对空目录实测可能返回 `data:null`。实现选择可分页的 `created/list`，保留所有目录；收藏内容 `medias:null` 可表示空夹。

## 登录凭据和失败边界

- 轮询的已知状态：86101 未扫码、86090 已扫码待确认、86038 已过期、0 已确认。只有确认响应才收集 Cookie。
- Cookie 从 `Set-Cookie` 提取；兼容可信 B站回调 URL 中的旧式 Cookie 查询参数，也支持可信 SSO 回调下发 Cookie。请求不自动跟随重定向。新式回调可能使用一次性票据，失败后重新扫码。
- 保存前必须调用 `nav` 并检查 `isLogin=true`、`mid>0`。只看到二维码状态成功不足以判断登录完成。
- 回调查询参数按 URL 规则解码一次；SESSDATA 的原始逗号再规范为 `%2C`，已有百分号转义保留。单层/双层编码已经合成响应测试覆盖，真实账号回调仍待设备验证。
- `SESSDATA`、`bili_jct`、`DedeUserID`、`DedeUserID__ckMd5`、`sid` 是允许保存的 Cookie 名称；本实现不持久化刷新令牌，过期后重新扫码。
- Cookie 使用 AndroidKeyStore AES/GCM 加密后写入私有 SharedPreferences；账号 Cookie 仅发往 B站 API/Passport，不带到音频 CDN。
- 本次只实测了生成和未扫码轮询。手机扫码确认、真实账号 Cookie、私有收藏夹、账号失效恢复仍需用户设备验收。

## 播放与分页策略

- DASH 在这里表示接口提供分离的音视频资源；选择 `dash.audio` 中兼容的 AAC 音轨，按 bandwidth 选择较高码率。兼容 camelCase/snake_case 地址字段。
- 实际对独立音轨发 `Range: bytes=0-1023`，仅读前32字节后关闭：HTTP **206**、文件头 `ftyp`，证明音轨地址能返回 MP4 容器字节。CDN 竟返回 `Content-Type: video/mp4`，因此必须按 API 的 audio 数组选轨，不能仅凭 HTTP MIME 判轨。
- URL 中实际出现 `deadline`、`upsig` 等参数。不要把流地址作为曲目身份或长久缓存；队列持久化 `bvid:cid`，切歌时解析，失败后有限次数刷新。仅接受 HTTPS 播放地址。
- `fnval=0` 实测返回 durl（混合音视频 MP4），不能保证独立音轨；当前纯音频应用明确报“未提供独立音轨”，不悄悄下载视频。分段 durl 也不能只取第一段。
- 收藏夹及内容按 pn 从1递增，读取 has_more；跨页按 ID 去重，最多500页（每页20项），空页但 has_more=true / 全页重复时抛出异常。非视频媒体不进入播放库；失效视频保留不可播放标记。
- 错误处理分 HTTP 错误与 JSON code；-101 识别为登录失效，-403 为权限/资源问题，412/429/-352 为受限。错误消息不输出响应正文、Cookie 或签名流地址。

## 核对来源

- 一手接口探测见上表，可由项目脚本或测试设备重新执行；本次未保存临时二维码密钥、签名音频 URL 或凭据。
- [BiliBili-API 开源客户端及作者实测记录](https://github.com/abcLiyew/BiliBili-API)：二维码 Set-Cookie、nav 校验、playurl 与风控差异，仅作交叉核对。
- [bilibili_api.md 实测记录](https://github.com/daymade/claude-code-skills/blob/main/bilibili-source/references/bilibili_api.md)：收藏夹与内容分页字段、登录枚举差异；实现以本次真实接口响应为准。
- [Android 官方：MediaSessionService 后台播放](https://developer.android.com/media/media3/session/background-playback)：Player 与 MediaSession 由服务持有，声明 mediaPlayback 前台服务类型及对应权限。
- [Android 官方：ExoPlayer 支持格式](https://developer.android.com/media/media3/exoplayer/supported-formats)：可直接播放 MP4/M4A/FMP4 容器；本项目将单独 AAC 流交给 Media3。
- [Android 官方：数据源定制](https://developer.android.com/media/media3/exoplayer/customization)：自定义 DataSource/ResolvingDataSource 可注入 Referer 与即时解析 URI。

## 离线验证

`BiliRepositoryTest` 使用 MockWebServer 合成响应测试分页、去重、坏分页、非视频过滤、失效标记、扫码 Cookie 仅成功后保存、nav 复核、可信回调、AAC选轨、HTTPS过滤和错误脱敏。测试不登录真实账号，也不连接真实 CDN。

## 2026-10-15 / 本机 App 入口与验收更新

- 用户已真实扫码登录并看到收藏夹；API35模拟器独立音轨实际播放与后台进度已验证。
- 0.1.1 默认入口使用 `bilibili://browser?url=<encoded 完整二维码确认URL>`，显式交给官方 App，保留原 qrcode_key 继续轮询，并仍以nav确认账号；打不开时展示同一会话二维码。
- 2026-10-15，用户已在当前官方App版本确认授权并成功登录，验证了本机入口的完整会话回传。[公开作者的原始实现](https://www.yckceo.com/yuedu/shuyuan/content/id/4960.html)使用同类scheme和轮询链路，可作交叉核对，实际确认URL始终采用本次接口返回值。

`LocalPersistenceTest` 为设备端测试：使用每次随机生成的 SharedPreferences 名称及 AndroidKeyStore 别名，验证加密往返、重复加密随机性、密文篡改清除、置顶在账号和类别之间隔离。测试数据均为合成值，清理仅作用于测试专属文件与别名。

## 供设备验证的公开多 P 样本

再次实际核对视频详情和音轨接口，未添加任何生产演示入口：

| 视频 | 分 P 总数 | 样本 | 秒数 | 音轨解析结果 |
| --- | --- | --- | --- | --- |
| [BV1hv411e77b](https://www.bilibili.com/video/BV1hv411e77b/) | 23 | P6，cid=306996376；过场广告音乐－钢琴版琴谱 | 19 | code=0，3条AAC；适合自动切歌测试 |
| 同上 | 23 | P2，cid=316663804 | 115 | 已获取分 P 元数据，可用作队列第二首 |
| [BV1e44y1r7Hb](https://www.bilibili.com/video/BV1e44y1r7Hb/) | 41 | P9，cid=341102135；车尔尼第四册第四首 | 60 | code=0，1条AAC |

元数据和取流成功不等于已经通过 Android 真机播放；Media3解码、seek、后台和自动切歌需以设备测试结果为准。
