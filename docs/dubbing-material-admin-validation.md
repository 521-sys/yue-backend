# 电影配音素材管理验证记录

核对时间：2026-10-23（Asia/Shanghai）。本记录描述部署前的本地候选验证；其后已完成生产发布，结果见 [生产发布记录](dubbing-material-admin-release-2026-10-22.md)。

## 已验证范围

- 数据库迁移与种子：隔离 MySQL 8 实例执行迁移、种子与重复执行检查通过，结果为 2 条素材、6 个 Part、25 句台词；Java MySQL 专项测试 1 项通过。
- 视频导入器：4 项 Python 测试通过，覆盖清单校验、媒体探测、幂等导入与冲突拒绝。
- 后端完整测试：152 项，0 失败、0 错误、4 项按外部 MySQL 环境条件跳过；随后 `mvn -DskipTests package` 成功。
- 管理端完整测试：4 个文件、14 项全部通过；生产构建成功。
- 用户网页完整测试：25 个文件、206 项全部通过；邮箱登录开启、短信入口关闭的生产构建成功。
- 用户网页构建不包含 `dist/dubbing-clips`，生产源码不再引用 `/dubbing-clips/` 或静态 `verifiedDubbingMaterials`；素材清单改从 `/api/public/dubbing-materials` 获取。
- 本机浏览器视觉检查受 Codex 内置浏览器无法访问宿主机 localhost 限制，未作为通过依据；交互行为由组件测试覆盖，生产浏览器仍需发布后验收。

## 可恢复视频归档

移除用户网页的旧静态副本前，六个 MP4 已复制到 `release-staging/dubbing-admin-20261022-006/seed-source/`，完整 SHA-256 清单随目录保存。压缩包 `dubbing-seed-source.zip` 已实际展开，并再次核对 6 个文件数量、大小与哈希。

- 归档 SHA-256：`843d9649da3ca7ee8097b658e499b72ccb87b36476bc6bd887e634fe05fd61d3`
- `pendatang-arrival-01.mp4`：`713c519feedf6f692d6d5ea7d08c436a7846326fa045289b97a8d8f60c4b5116`
- `pendatang-arrival-02.mp4`：`6ff8729ea3b25f7275fe94f272bbddd5e8fa785196d840d69db846b5ead319ba`
- `pendatang-arrival-03.mp4`：`7d3c75601d8596774c81eee8b8a842b9f78c655a2475087c94ff752217f44e6f`
- `wash-hands-01.mp4`：`016ad0ebec3ca0b5c9bb423a59b9e3eef5f8058ce5be7ef85bdb14cf9c021e35`
- `wash-hands-02.mp4`：`3a185ec48b72dd764681e56f1ea800325cddf87fe9ef3d383ea8e7b3ee36ec60`
- `wash-hands-03.mp4`：`1c94dad9f60c99cddcf9f98894195e4d72a0e02c4b012b3560d849a4d31a223f`

## 候选制品

- 后端 JAR：`target/yue-backend-1.0.0.jar`，SHA-256 `0165597cd8c013ebad6c2a10d9df2244a7d079d611085ea8bc89df6e5087f487`
- 管理端：`index-CzmSYlSA.js` / `index-C1DnqtFv.css`
- 用户网页：`index-ChPTvz92.js` / `index-CkeHLtz6.css`

这些指纹只对应本地候选制品。生产是否成功必须以协调备份、迁移演练、原子切换及外网验收的最新记录为准。
