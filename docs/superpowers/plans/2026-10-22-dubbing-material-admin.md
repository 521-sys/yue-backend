# 电影配音素材后台管理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将电影配音素材、Part、视频和台词迁入运营后台管理，并让用户网页只消费已发布的后台内容。

**Architecture:** Spring Boot/MySQL 保存规范化素材元数据，本地受管目录保存经 FFprobe 校验的 Part MP4；管理员 API 管理全生命周期，公开 API 只提供已发布清单和支持 Range 的视频。React 管理端新增电影素材页面，React 用户端异步读取公开清单并复用现有配音工作区。

**Tech Stack:** Java 21、Spring Boot 3.2、Spring Data JPA、MySQL 8/H2、FFprobe、React 19、TypeScript、Vite、Vitest、Testing Library、Python 3 标准库迁移助手

**Spec:** `docs/superpowers/specs/2026-10-22-dubbing-material-admin-design.md`

## Global Constraints

- 第一版只上传已经裁剪好的 Part MP4，不实现整片上传、在线裁剪或转码。
- 视频必须为 MP4/H.264/AAC，单段不超过 200 MiB、5 分钟；真实媒体信息只信任 FFprobe。
- 生产继续使用 Hibernate `validate`，数据库变化必须由显式、可重复执行的 MySQL 8 脚本完成。
- 生产视频目录固定为 `/opt/yue/dubbing-videos`；数据库和该目录是同一备份/恢复单元。
- 保留现有两个 material code、六个 part code 和 25 个 cue code，不破坏浏览器已有草稿。
- 用户端不回退静态素材清单；公开接口失败时保留重试和“导入我的视频”。
- 视频必须流式响应并支持 Range，禁止使用 `readAllBytes` 将整个视频载入 JVM 内存。
- 保留三个仓库现有修改和未跟踪文件；每次提交只暂存任务明确列出的文件，不清理 `.superpowers/`、`dist.tgz`、模型、素材源文件或浏览器数据。
- 不修改后端现有 ASR、短信、邮箱、场景和音频接口行为。

## Review Focus

- `Range` 的开放尾段、后缀区间和越界输入必须分别得到正确 `206`/`416`，并由 Task 2 的真实文件响应测试覆盖。
- 视频替换在 FFprobe 成功后、数据库提交前失败时必须删除新文件并保留旧文件，由 Task 3 的事务回滚测试覆盖。
- MySQL 默认不区分大小写时，`Part-01` 与 `part-01` 不能成为两个稳定编码，由 Task 1 的迁移约束测试覆盖。
- 用户导入自己的视频后，迟到的素材清单请求不能把页面切回内置素材，由 Task 6 的异步竞态测试覆盖。
- 已发布素材如果因异常数据缺少视频或台词，公开接口必须整体失败而不是返回半份素材，由 Task 3 的损坏数据测试覆盖。

---

### Task 1: 数据库模型与幂等迁移

**Files:**
- Create: `src/main/java/com/yue/entity/DubbingMaterial.java`
- Create: `src/main/java/com/yue/entity/DubbingPart.java`
- Create: `src/main/java/com/yue/entity/DubbingCue.java`
- Create: `src/main/java/com/yue/repository/DubbingMaterialRepository.java`
- Create: `src/main/java/com/yue/repository/DubbingPartRepository.java`
- Create: `src/main/java/com/yue/repository/DubbingCueRepository.java`
- Create: `scripts/dubbing-material-migration.sql`
- Create: `src/test/java/com/yue/dubbingmaterial/DubbingMaterialSchemaTest.java`
- Create: `src/test/java/com/yue/dubbingmaterial/DubbingMaterialMysqlMigrationTest.java`

**Interfaces:**
- Produces `DubbingMaterial` with `code`, metadata, `status`, `sortOrder`, `version`, timestamps.
- Produces `DubbingPart` with stable `code`, video metadata, `videoVersion`, `version`, and `materialId`.
- Produces `DubbingCue` with stable `code`, `sequenceNo`, `startMs`, `endMs`, `text`, and `partId`.
- Repositories provide ordered child reads plus material search through `JpaSpecificationExecutor<DubbingMaterial>`.

- [ ] **Step 1: Write failing schema tests**

  Add tests asserting new materials default to `DRAFT`, all three stable code scopes are unique, cascade order is explicit, and `Part-01`/`part-01` collide under the production collation.

- [ ] **Step 2: Run the tests and verify RED**

  Run: `mvn -Dtest=DubbingMaterialSchemaTest test`
  Expected: compilation/test failure because entities and repositories do not exist.

- [ ] **Step 3: Implement entities, repositories, and migration**

  Use the exact fields and limits in the spec. Add foreign keys from parts to materials and cues to parts, ordered indexes, case-insensitive unique constraints, and a repeatable stored-procedure-style migration matching `admin-v1-migration.sql`.

- [ ] **Step 4: Verify H2 and opt-in MySQL migration behavior**

  Run: `mvn -Dtest=DubbingMaterialSchemaTest test`
  Expected: PASS.

  Run against the disposable loopback MySQL used by existing migration tests: `mvn -Dadmin.mysql=true -Dtest=DubbingMaterialMysqlMigrationTest test`
  Expected: running the migration twice succeeds; Hibernate `validate` starts; duplicate stable codes differing only by case are rejected.

- [ ] **Step 5: Commit Task 1**

  ```bash
  git add src/main/java/com/yue/entity/DubbingMaterial.java src/main/java/com/yue/entity/DubbingPart.java src/main/java/com/yue/entity/DubbingCue.java src/main/java/com/yue/repository/DubbingMaterialRepository.java src/main/java/com/yue/repository/DubbingPartRepository.java src/main/java/com/yue/repository/DubbingCueRepository.java scripts/dubbing-material-migration.sql src/test/java/com/yue/dubbingmaterial/DubbingMaterialSchemaTest.java src/test/java/com/yue/dubbingmaterial/DubbingMaterialMysqlMigrationTest.java
  git commit -m "feat: persist dubbing materials"
  ```

### Task 2: 安全视频存储、媒体校验与 Range 读取

**Files:**
- Create: `src/main/java/com/yue/dubbingmaterial/DubbingVideoStorage.java`
- Create: `src/main/java/com/yue/dubbingmaterial/DubbingVideoProbe.java`
- Create: `src/main/java/com/yue/dubbingmaterial/DubbingVideoResponse.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/java/com/yue/dubbing/DubbingUploadFilter.java`
- Create: `src/test/java/com/yue/dubbingmaterial/DubbingVideoStorageTest.java`
- Create: `src/test/java/com/yue/dubbingmaterial/DubbingVideoResponseTest.java`
- Modify: `src/test/java/com/yue/dubbing/DubbingUploadFilterTest.java`

**Interfaces:**
- `DubbingVideoStorage.StoredVideo store(MultipartFile file)` writes a validated temporary upload and atomically promotes it.
- `Path path(String fileName)` rejects traversal; `void remove(String fileName)` is idempotent.
- `DubbingVideoProbe.Metadata probe(Path path)` returns duration, container, video codec, and audio codec with a bounded timeout.
- `ResponseEntity<ResourceRegion> DubbingVideoResponse.respond(Path path, String contentType, String etag, HttpHeaders requestHeaders)` implements single-range streaming.

- [ ] **Step 1: Write failing storage and HTTP behavior tests**

  Cover valid fixture upload, fake MP4 extension, wrong codecs, missing audio, 200 MiB boundary without allocating one giant byte array, duration over five minutes, probe timeout, traversal, full response, `bytes=0-99`, `bytes=100-`, `bytes=-100`, multi-range rejection, and unsatisfiable range `416`.

- [ ] **Step 2: Run the tests and verify RED**

  Run: `mvn -Dtest=DubbingVideoStorageTest,DubbingVideoResponseTest,DubbingUploadFilterTest test`
  Expected: compilation/test failure because the video components and admin video upload allowance do not exist.

- [ ] **Step 3: Implement minimal streaming storage and Range support**

  Generate server-side UUID filenames, use same-directory temporary files, validate magic plus FFprobe output, configure `admin.dubbing-video-dir`, and extend the upload filter so only the authenticated movie-video routes receive the 201 MiB request ceiling and shared upload semaphore.

- [ ] **Step 4: Run Task 2 tests**

  Run: `mvn -Dtest=DubbingVideoStorageTest,DubbingVideoResponseTest,DubbingUploadFilterTest test`
  Expected: PASS, including suffix/open-ended ranges and `416`.

- [ ] **Step 5: Commit Task 2**

  ```bash
  git add src/main/java/com/yue/dubbingmaterial/DubbingVideoStorage.java src/main/java/com/yue/dubbingmaterial/DubbingVideoProbe.java src/main/java/com/yue/dubbingmaterial/DubbingVideoResponse.java src/main/resources/application.yml src/main/java/com/yue/dubbing/DubbingUploadFilter.java src/test/java/com/yue/dubbingmaterial/DubbingVideoStorageTest.java src/test/java/com/yue/dubbingmaterial/DubbingVideoResponseTest.java src/test/java/com/yue/dubbing/DubbingUploadFilterTest.java
  git commit -m "feat: store and stream dubbing videos"
  ```

### Task 3: 管理生命周期、公开清单与视频接口

**Files:**
- Create: `src/main/java/com/yue/dubbingmaterial/DubbingMaterialDtos.java`
- Create: `src/main/java/com/yue/dubbingmaterial/AdminDubbingMaterialService.java`
- Create: `src/main/java/com/yue/dubbingmaterial/AdminDubbingMaterialController.java`
- Create: `src/main/java/com/yue/dubbingmaterial/PublicDubbingMaterialService.java`
- Create: `src/main/java/com/yue/dubbingmaterial/PublicDubbingMaterialController.java`
- Modify: `src/main/java/com/yue/admin/AdminAuditService.java` only if target validation is currently closed
- Create: `src/test/java/com/yue/dubbingmaterial/AdminDubbingMaterialFlowTest.java`
- Create: `src/test/java/com/yue/dubbingmaterial/PublicDubbingMaterialTest.java`
- Create: `src/test/java/com/yue/dubbingmaterial/DubbingVideoTransactionTest.java`

**Interfaces:**
- Admin endpoints and public endpoints exactly match spec sections 7.1 and 7.2.
- Save DTOs use `materialVersion` plus child `version` values; public DTOs use stable codes as `id` fields and never expose database ids or storage filenames.
- Public video URLs include `?v={videoVersion}`.

- [ ] **Step 1: Write failing admin/public integration tests**

  Exercise create, edit, ordering, upload, publish, public read, offline, delete, audit, role protection, version conflict, publication validation, published destructive-operation rejection, and public draft-video `404`. Add a repository-level corruption fixture proving a published material missing a video or cues makes the whole public list fail.

- [ ] **Step 2: Write the failing file transaction tests**

  Force database rollback after a successful new-file probe and assert the new file is removed while the old referenced file remains; separately make old-file deletion fail after commit and assert the committed new reference still works and a warning is emitted.

- [ ] **Step 3: Run the tests and verify RED**

  Run: `mvn -Dtest=AdminDubbingMaterialFlowTest,PublicDubbingMaterialTest,DubbingVideoTransactionTest test`
  Expected: compilation/test failure because services and controllers do not exist.

- [ ] **Step 4: Implement services, controllers, DTO mapping, validation and cleanup synchronization**

  Follow existing `AdminSceneService` optimistic-lock and audit patterns. Lock the owning material for video/Part mutations, register new/old file cleanup with `TransactionSynchronization`, and use Task 2 response handling for both admin and public video routes.

- [ ] **Step 5: Run Task 3 and existing admin regressions**

  Run: `mvn -Dtest=AdminDubbingMaterialFlowTest,PublicDubbingMaterialTest,DubbingVideoTransactionTest,AdminFlowTest,AdminAudioUploadTest test`
  Expected: PASS.

- [ ] **Step 6: Commit Task 3**

  ```bash
  git add src/main/java/com/yue/dubbingmaterial src/test/java/com/yue/dubbingmaterial src/main/java/com/yue/admin/AdminAuditService.java
  git commit -m "feat: manage and publish dubbing materials"
  ```

### Task 4: 现有两套素材的可重复导入

**Files:**
- Create: `scripts/dubbing-material-seed.sql`
- Create: `scripts/import-dubbing-material-videos.py`
- Create: `scripts/tests/test_import_dubbing_material_videos.py`
- Modify: `src/test/java/com/yue/dubbingmaterial/DubbingMaterialMysqlMigrationTest.java`
- Modify: `docs/admin-v1-setup.md`

**Interfaces:**
- `import-dubbing-material-videos.py --source-dir <six-mp4-dir> --target-dir <managed-dir>` verifies the six spec hashes, probes metadata, and atomically creates deterministic seed filenames without overwriting mismatched files.
- `dubbing-material-seed.sql` upserts only the known seed codes when absent and never overwrites later operator edits.

- [ ] **Step 1: Write failing importer and seed tests**

  Python tests cover correct copy, wrong hash, interrupted temporary copy, repeat execution, and mismatched existing target. Extend the MySQL test to execute migration/seed twice and assert 2 materials, 6 Parts, 25 cues, stable codes, ordering, published status, and preservation of a simulated operator edit.

- [ ] **Step 2: Run tests and verify RED**

  Run: `python -m unittest scripts.tests.test_import_dubbing_material_videos -v`
  Expected: import failure because the helper does not exist.

  Run: `mvn -Dadmin.mysql=true -Dtest=DubbingMaterialMysqlMigrationTest test`
  Expected: FAIL because seed data does not exist.

- [ ] **Step 3: Implement importer, seed SQL and operating instructions**

  Use the current six files from `../yue-frontend/public/dubbing-clips` only as migration input; do not download from the internet. Store the exact existing material/Part/cue codes and metadata from `src/data/dubbing-materials.ts`.

- [ ] **Step 4: Run importer and MySQL migration tests GREEN**

  Run both commands from Step 2.
  Expected: PASS; reruns preserve the simulated operator edit and all six file hashes.

- [ ] **Step 5: Commit Task 4**

  ```bash
  git add scripts/dubbing-material-seed.sql scripts/import-dubbing-material-videos.py scripts/tests/test_import_dubbing_material_videos.py src/test/java/com/yue/dubbingmaterial/DubbingMaterialMysqlMigrationTest.java docs/admin-v1-setup.md
  git commit -m "feat: seed existing dubbing materials"
  ```

### Task 5: 管理后台电影素材页面

**Files (repository `yue-admin`):**
- Modify: `src/App.tsx`
- Modify: `src/types.ts`
- Modify: `src/api.ts`
- Modify: `src/style.css`
- Create: `src/pages/DubbingMaterials.tsx`
- Create: `src/pages/DubbingMaterialEditor.tsx`
- Create: `src/components/DubbingPartEditor.tsx`
- Create: `src/components/DubbingCueEditor.tsx`
- Create: `src/__tests__/dubbing-materials.test.tsx`
- Modify: `src/__tests__/admin-flow.test.tsx`

**Interfaces:**
- `DubbingMaterialDetail` mirrors the admin DTO from Task 3.
- `DubbingPartEditor` emits complete ordered Part values and a separate upload/replace intent; it never invents duration or codec metadata.
- Navigation keys are `dubbing` and `dubbing/{id}`.

- [ ] **Step 1: Write failing page-flow tests**

  Cover menu navigation, list search/status/empty/retry, create/edit fields, tags, Part and cue ordering, MP4 upload, server validation errors, preview URL, dirty navigation, version conflict preserving inputs, publish/offline, and disabling replace/delete while published.

- [ ] **Step 2: Run tests and verify RED**

  Run: `npm test -- src/__tests__/dubbing-materials.test.tsx src/__tests__/admin-flow.test.tsx`
  Expected: FAIL because the movie menu and pages do not exist.

- [ ] **Step 3: Implement types, pages, components and API calls**

  Follow the existing scene list/editor visual language. Keep large-file upload state separate from JSON save state; show backend duration/codecs after upload; label every time input and upload control for accessibility.

- [ ] **Step 4: Run Task 5 tests and build**

  Run: `npm test -- src/__tests__/dubbing-materials.test.tsx src/__tests__/admin-flow.test.tsx`
  Expected: PASS.

  Run: `npm run build`
  Expected: exit 0.

- [ ] **Step 5: Commit Task 5**

  ```bash
  git add src/App.tsx src/types.ts src/api.ts src/style.css src/pages/DubbingMaterials.tsx src/pages/DubbingMaterialEditor.tsx src/components/DubbingPartEditor.tsx src/components/DubbingCueEditor.tsx src/__tests__/dubbing-materials.test.tsx src/__tests__/admin-flow.test.tsx
  git commit -m "feat: manage dubbing materials in admin"
  ```

### Task 6: 用户网页改读公开素材 API

**Files (repository `yue-frontend`):**
- Modify: `src/data/dubbing-materials.ts`
- Create: `src/lib/dubbing-materials-api.ts`
- Modify: `src/screens/MovieImitationScreen.tsx`
- Modify: `src/__tests__/movie.test.tsx`
- Modify: `src/__tests__/movie-persistence.test.tsx`
- Create: `src/__tests__/dubbing-materials-api.test.ts`

**Interfaces:**
- `loadDubbingMaterials(signal?: AbortSignal): Promise<DubbingMaterial[]>` requests `/api/public/dubbing-materials` and validates the complete nested payload before returning it.
- `MovieImitationScreen({materials})` retains explicit injected materials for focused tests; omitted `materials` triggers asynchronous API loading.

- [ ] **Step 1: Write failing API/loading tests**

  Replace default-static assertions with public payload fixtures. Cover loading, success ordering, empty list, retry, malformed payload, request failure, no static fallback, current codes restoring saved drafts, unavailable saved material, and user import while the public request is still pending; the late response must not replace the imported video.

- [ ] **Step 2: Run tests and verify RED**

  Run: `npm test -- src/__tests__/dubbing-materials-api.test.ts src/__tests__/movie.test.tsx src/__tests__/movie-persistence.test.tsx`
  Expected: FAIL because default rendering still reads the static array and has no loading/retry flow.

- [ ] **Step 3: Implement payload validation and asynchronous screen state**

  Keep only shared interfaces in `dubbing-materials.ts`; remove `verifiedDubbingMaterials`. Abort fetch on unmount, use a generation guard when users import or switch sources, and never overwrite an existing IndexedDB project on list failure.

- [ ] **Step 4: Run Task 6 tests and production build**

  Run: `npm test -- src/__tests__/dubbing-materials-api.test.ts src/__tests__/movie.test.tsx src/__tests__/movie-persistence.test.tsx`
  Expected: PASS.

  Run with production flags: `$env:VITE_EMAIL_LOGIN_ENABLED='true'; $env:VITE_SMS_LOGIN_ENABLED='false'; npm run build`
  Expected: exit 0.

- [ ] **Step 5: Commit Task 6 without scooping unrelated files**

  Review `git diff` first because this checkout already contains the deployed movie-workspace changes. Stage only the listed source/test files plus any previously untracked `src/data/dubbing-materials.ts` dependency that is now finalized.

  ```bash
  git add src/data/dubbing-materials.ts src/lib/dubbing-materials-api.ts src/screens/MovieImitationScreen.tsx src/__tests__/movie.test.tsx src/__tests__/movie-persistence.test.tsx src/__tests__/dubbing-materials-api.test.ts
  git commit -m "feat: load published dubbing materials"
  ```

### Task 7: 移除前端静态视频并完成三仓库回归

**Files:**
- Remove after verified seed copy: `yue-frontend/public/dubbing-clips/*.mp4`
- Modify: `yue-backend/docs/admin-v1-validation.md` if present, otherwise create `yue-backend/docs/dubbing-material-admin-validation.md`
- Modify: root `项目现状总结.md`
- Modify: root `docs/粤语配音开发记录.md`

**Interfaces:**
- A local seed artifact contains all six MP4s and a SHA-256 manifest before frontend copies are removed.
- Frontend `dist` contains no `dubbing-clips` directory and no hard-coded source material list.

- [ ] **Step 1: Create and verify a recoverable seed artifact**

  Copy the six clips to a new release-staging directory, write a SHA-256 manifest, compare all values with the spec, and confirm the archive can be listed and extracted before removing the frontend copies. Do not alter original full-source media or existing release backups.

- [ ] **Step 2: Remove only the six verified frontend clip copies and rebuild**

  Delete the six exact files under `yue-frontend/public/dubbing-clips`; remove the directory only if empty. Rebuild and assert `dist/dubbing-clips` is absent.

- [ ] **Step 3: Run fresh full verification in all repositories**

  Backend: `mvn test` then `mvn -DskipTests package`.

  Admin: `npm test` then `npm run build`.

  Frontend: `npm test` then `$env:VITE_EMAIL_LOGIN_ENABLED='true'; $env:VITE_SMS_LOGIN_ENABLED='false'; npm run build`.

  Expected: all exit 0; report exact test counts and any conditional skips.

- [ ] **Step 4: Update validation and workspace records with actual evidence**

  Record media hashes, migration test results, build assets, known limits, and the fact that deployment has not yet occurred. Update root current-state text without claiming production success.

- [ ] **Step 5: Commit repository-owned Task 7 changes separately**

  Commit only backend validation docs in `yue-backend` and only the verified movie-feature/static-removal changes in `yue-frontend`. Root documents remain local because the root is not a Git repository. Do not push.

### Task 8: 协调生产迁移、部署与真实验收

**Files:**
- Create under root release staging: deployment, preflight, rollback, and acceptance scripts plus artifact manifests.
- Update after acceptance: `yue-backend/docs/dubbing-material-admin-release-2026-10-22.md`
- Update after acceptance: root `项目现状总结.md`
- Update after acceptance: root `docs/粤语配音开发记录.md`

**Interfaces:**
- One release manifest binds database migration/seed hashes, six video hashes, backend JAR, admin bundle, user bundle, and coordinated backup path.
- Acceptance scripts use HTTP/1.1, exact asset hashes, API payload counts, Range checks, permission gates, and service status.

- [ ] **Step 1: Build signed-by-hash release artifacts and read-only preflight**

  Verify current production fingerprints, free disk, MySQL/nginx/yue-backend status, `nginx -t`, existing routes, and exact local artifacts. Upload candidates without switching production.

- [ ] **Step 2: Create coordinated backup and rehearse migration**

  Back up the production database, `/opt/yue/dubbing-videos` if present, backend/config, `/var/www/yue`, and `/var/www/yue-admin`. Restore the backup into an isolated database and temporary video directory, run migration/import twice, start the candidate backend with `validate`, and verify 2/6/25 counts plus all hashes.

- [ ] **Step 3: Execute ordered production deployment with automatic stop/rollback gates**

  Apply migration and seed, import six files, deploy backend, then admin, then user frontend. Do not switch a later component if an earlier acceptance gate fails. Preserve production email enabled, SMS hidden, ASR enabled, and the existing 32,400-second shared budget.

- [ ] **Step 4: Run API and browser acceptance**

  Verify administrator authentication; material list/edit/upload/publish/offline/delete with a temporary fixture; public list contains exactly the two migrated materials in order; draft and offline videos return `404`; all six seed videos return `200` and `206` for Range; user movie page displays both materials and opens all Parts; local import and saved-project recovery still work. Clean the temporary fixture and prove no residue.

- [ ] **Step 5: Record actual deployment evidence and commit repository docs**

  Write the final backup path, artifact hashes, database counts, HTTP statuses, service states, browser results, rollback status, and any unverified item. Run `git diff --check` in all three repositories. Commit release documentation only after evidence exists; do not push unless the user explicitly asks.

