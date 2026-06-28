# App Local Dev Profile Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a local app integration profile so the Spring Boot backend can run without PostgreSQL and the Android App can connect from LDPlayer through `10.0.2.2:8080`.

**Architecture:** Keep production defaults unchanged. Add a dedicated `app-dev` profile, a lightweight PowerShell orchestration script, and documentation for the emulator integration workflow.

**Tech Stack:** Spring Boot profiles, H2 file database, PowerShell, Android Gradle, LDPlayer adb.

---

### Task 1: Backend Profile Test

**Files:**
- Create: `backend/src/test/java/com/reelshort/backend/system/AppDevProfileContextTests.java`

**Step 1: Write test**

Add a `@SpringBootTest` using `@ActiveProfiles("app-dev")` and assert context loads.

**Step 2: Run focused test**

Run:

```powershell
cd backend
.\gradlew.bat test --tests "*AppDevProfileContextTests" --no-daemon
```

Expected: fail until `application-app-dev.properties` exists and H2 is available at runtime.

### Task 2: Backend app-dev Profile

**Files:**
- Create: `backend/src/main/resources/application-app-dev.properties`
- Modify: `backend/build.gradle`

**Step 1: Add runtime H2**

Move H2 from `testRuntimeOnly` to `runtimeOnly` or add runtime dependency so app-dev can start outside tests.

**Step 2: Add profile properties**

Use H2 file database, disable Flyway, use JPA `update`, disable rate limit, point content-provider to `127.0.0.1:5000`.

**Step 3: Verify focused test**

Run the focused backend test and expect pass.

### Task 3: Local Dev Script And Docs

**Files:**
- Create: `infra/scripts/start-app-local-dev.ps1`
- Create: `docs/deploy/app-local-dev.md`
- Modify: `README.md`
- Modify: `AGENTS.md`

**Step 1: Script**

Script starts content-provider, starts backend with `--spring.profiles.active=app-dev`, builds APK, installs and launches via LDPlayer adb.

**Step 2: Docs**

Document prerequisites, commands, ports, emulator address, and shutdown.

### Task 4: Verification

Run:

```powershell
cd backend
.\gradlew.bat test --no-daemon
cd ..\content-provider
pytest
cd ..\android-app
.\gradlew.bat :app-core:test --no-daemon
.\gradlew.bat :app:assembleDebug --no-daemon
cd ..
git diff --check
```

Then install and start APK through LDPlayer adb and check `pidof` plus logcat.
