# Dubbing Material Covers Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement task-by-task in the current session.

**Goal:** Display an uploaded cover image on each mini-program movie material card.
**Architecture:** Material-level image metadata and protected file storage in the backend; versioned admin upload and public read; admin editor upload panel; mini-program image with fallback.
**Tech Stack:** Java 21 / Spring Boot, ImageIO, MySQL SQL migration, React / TypeScript, Taro, Python / FFmpeg.
**Spec:** ../specs/2026-10-26-dubbing-material-cover-design.md

## Global Constraints

- JPEG/PNG only, maximum 5 MiB, maximum edge 4096, maximum 16,000,000 pixels; inspect dimensions before decoding.
- Files are staged atomically; rollback removes new files, commit removes old files. Published materials must be taken offline before replacement.
- Existing material covers: Part 01 at 0.5 seconds, 1280×720 JPEG. No production migration or deployment in this execution.
- Preserve current feature branches and existing work. Three repositories are independently committed.

## Review Focus

- Malformed images and decompression-size claims are rejected before full decoding (Task 1).
- Conflicts and transaction rollback retain old files (Task 2).
- Unpublished material covers are inaccessible publicly; stale versions revalidate safely (Task 2).
- Upload must not discard unsaved admin form changes (Task 3).
- Missing/broken covers preserve navigation and card layout (Task 4).

### Task 1: Image storage and metadata

Files: backend entity DubbingMaterial.java, new DubbingCoverStorage.java, configuration, scripts/dubbing-cover-migration.sql, DubbingCoverStorageTest.java.
Interfaces: StoredCover(fileName, contentType, sizeBytes, width, height); store(MultipartFile), path(String), remove(String).
- [x] Test JPEG/PNG acceptance, spoofed content, empty/oversize/dimension limits, bounded copy and path traversal.
- [x] Implement ImageIO header inspection then decoding, UUID file names and atomic move; add nullable metadata and idempotent MySQL migration.
- [x] Run storage tests and compile.

### Task 2: Versioned cover lifecycle and HTTP API

Files: backend DTOs, admin/public services/controllers, new DubbingCoverResponse.java, upload filter and lifecycle/transaction tests.
Interfaces: CoverItem(url, originalName, contentType, sizeBytes, width, height, coverVersion); PublicMaterial.coverUrl; POST/PUT cover with materialVersion, GET cover by material id/code.
- [x] Test upload/replacement/version conflicts/publication gate, admin permission, public visibility, ETag/304 and rollback cleanup.
- [x] Implement optimistic version check under material lock, transaction cleanup and audit; add 6 MiB multipart filter ceiling.
- [x] Run backend full tests and package.

### Task 3: Admin upload panel

Files: admin types.ts, new DubbingCoverEditor.tsx, material editor, dubbing.css and tests.
- [x] Test multipart upload with version, response preview, disabled states for new/dirty/published and visible errors.
- [x] Implement panel, authenticated blob preview and object URL cleanup; preserve current form on failure.
- [x] Run admin tests and build.

### Task 4: Mini-program image cards

Files: mini data types, API adapter, MaterialCatalog.tsx/scss and component/API tests.
- [x] Test relative/absolute/missing coverUrl, image rendering, failure fallback and card selection.
- [x] Implement coverSrc normalization, 16:9 aspectFill image, lazy loading and gradient fallback.
- [x] Run mini tests, typecheck and build.

### Task 5: Initial covers and delivery

Files: backend scripts/generate-dubbing-covers.py, seed cover manifest/assets, scripts/dubbing-cover-seed.sql, Python tests, deployment documentation and root status summary.
- [x] Generate two covers from approved clips with source hashes verified, inspect images and record SHA-256.
- [x] Implement repeatable import with hash verification and SQL updating only missing initial covers, preserving operator replacements.
- [x] Test repeat import and mismatch rejection; verify migration twice against available isolated MySQL or explicitly report unavailable verification.
- [x] Run final affected checks, independent read-only review, fix findings and commit each repository; update root summary with measured results.

Execution results and decisions: ../../dubbing-cover-implementation-2026-10-26.md. Kept existing clean feature checkouts per current-session workflow; the public API tolerates pre-upgrade missing covers, while new publication requires one. Symlink regression is conditionally skipped where Windows cannot create a link; the read guard is implemented. Production release remains a separate operation.
