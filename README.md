<div align="center">
  <h2>HMA-OSS</h2>

  <img src="HideMyAss-OSS.svg" alt="HMA-OSS Logo" style="max-width:360px;width:60%;height:auto;">

  <p>
    <a href="https://github.com/frknkrc44/HMA-OSS" style="text-decoration:none">
      <img src="https://img.shields.io/github/stars/frknkrc44/HMA-OSS?label=Stars&logo=github">
    </a>
    <a href="https://github.com/frknkrc44/HMA-OSS/actions" style="text-decoration:none">
      <img src="https://img.shields.io/github/actions/workflow/status/frknkrc44/HMA-OSS/main.yml?branch=master&logo=github">
    </a>
    <a href="https://github.com/frknkrc44/HMA-OSS/releases/latest" style="text-decoration:none">
      <img src="https://img.shields.io/github/v/release/frknkrc44/HMA-OSS?label=Release">
    </a>
    <a href="https://github.com/frknkrc44/HMA-OSS/releases/latest" style="text-decoration:none">
      <img src="https://img.shields.io/github/downloads/frknkrc44/HMA-OSS/total">
    </a>
    <a href="https://t.me/aerathfuns" style="text-decoration:none">
      <img src="https://img.shields.io/badge/Telegram-Channel-blue.svg?logo=telegram">
    </a>
    <a href="https://choosealicense.com/licenses/gpl-3.0/" style="text-decoration:none">
      <img src="https://img.shields.io/github/license/frknkrc44/HMA-OSS?label=License">
    </a>
  </p>
</div>

---

- **English**
- [中文（简体）](README_zh_CN.md)
- [Türkçe](README_tr.md)
- [日本語](README_ja.md)
- [Indonesia](README_id.md)
- [Português](README_pt.md)
- [Français](README_fr.md)

## 本 fork 的改动

基于 [frknkrc44/HMA-OSS](https://github.com/frknkrc44/HMA-OSS) 的 `master`，只改了 `zygote` 侧的两处，没有动管理器界面，也没有向上游提交。

| 提交 | 分支 | 内容 | 状态 |
|---|---|---|---|
| `9d9c06e` | `master` | `getCallingApps` 缓存 uid → 包名，避免每次查询都走 binder / PM | 可用 |
| `219e4c1` | `feat/apps-filter-cache` | 把隐藏关系直接写进 `AppsFilterImpl.mShouldFilterCache`，PM 查询不再经过 LSPlant 跳板 | **实验性，不要日常使用** |

`feat/apps-filter-cache` 实测的问题：

- 过度隐藏：`su <uid> -c "pm list packages"` 只剩 1 个包（正常约 450 个）
- 每次重新 apply 会短暂打断 PM 查询（日志里的 `Failed transaction`）
- 不解决 UID → 包名反查；那是另一条路径

构建：JDK 21，`:app:assembleDebug` 与 `:zygote:assembleDebug`（`zygote` 依赖 `app` 的产物，要先编 `app`）。release 需要签名配置。

### Changes in this fork

Based on `master` of [frknkrc44/HMA-OSS](https://github.com/frknkrc44/HMA-OSS). Only the `zygote` side is touched; the manager UI is unchanged and nothing was sent upstream.

- `9d9c06e` (`master`): cache uid → package in `getCallingApps` instead of hitting binder/PM on every query.
- `219e4c1` (`feat/apps-filter-cache`): write hide pairs into `AppsFilterImpl.mShouldFilterCache` so PM queries skip the LSPlant trampoline. **Experimental.** It over-hides (`pm list packages` returns 1 package instead of ~450) and briefly interrupts PM queries on every apply; it does not address UID → package lookups.

Build: JDK 21, `:app:assembleDebug` then `:zygote:assembleDebug` (`zygote` needs the app artifact). Release builds need the signing config.

## About this module

Although it's bad practice to detect the installation of specific apps, not every app using root provides random package name support. In this case, if apps related to root (such as Fake Location and Storage Isolation) are detected, it is tantamount to detecting that the device is rooted.

Additionally, some apps use various loopholes to acquire your app list, in order to use it as fingerprinting data or for other nefarious purposes.

This module can work as an Zygisk module to hide apps or reject app list requests.

## About HMA-OSS

https://github.com/frknkrc44/HMA-OSS/wiki

## I want to contribute translation
You can contribute translation [here](https://crowdin.com/project/frknkrc44-hma-oss).

## Update log
[Reference to the commits page](https://github.com/frknkrc44/HMA-OSS/commits)
