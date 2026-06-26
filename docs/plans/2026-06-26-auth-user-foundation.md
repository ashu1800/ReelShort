# Auth/User Foundation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build the backend ordinary-user registration and login foundation for phase 1.

**Architecture:** Keep auth use cases in `auth` and user persistence in `user`. Use JPA for the durable user boundary, BCrypt for password hashing, and an opaque token service that can later be replaced by JWT without changing controller contracts.

**Tech Stack:** Java 17, Spring Boot 3.4, Spring Web, Spring Validation, Spring Data JPA, H2 test runtime, BCrypt from Spring Security Crypto.

---

### Task 1: Dependencies and Auth Error Contract

**Files:**
- Modify: `backend/build.gradle`
- Create: `backend/src/main/java/com/reelshort/backend/auth/AuthException.java`
- Modify: `backend/src/main/java/com/reelshort/backend/system/web/GlobalExceptionHandler.java`
- Test: `backend/src/test/java/com/reelshort/backend/auth/AuthControllerTests.java`

**Steps:**
1. Write failing controller tests for duplicate registration `409`, invalid login `401`, and disabled login `403`.
2. Run the targeted test and verify it fails because auth endpoints/classes do not exist.
3. Add JPA, H2 test runtime, and Spring Security Crypto dependencies.
4. Add `AuthException` carrying an HTTP status code.
5. Map `AuthException` in `GlobalExceptionHandler`.
6. Run targeted tests; they may still fail until endpoint implementation exists.

### Task 2: User Persistence Model

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/user/UserStatus.java`
- Create: `backend/src/main/java/com/reelshort/backend/user/UserAccount.java`
- Create: `backend/src/main/java/com/reelshort/backend/user/UserAccountRepository.java`
- Test: `backend/src/test/java/com/reelshort/backend/user/UserAccountRepositoryTests.java`

**Steps:**
1. Write failing repository test proving usernames are unique and password hash is persisted.
2. Run the targeted repository test and verify it fails because model/repository does not exist.
3. Implement the entity, enum, and repository.
4. Run repository test and verify it passes.

### Task 3: Auth Service

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/auth/AuthService.java`
- Create: `backend/src/main/java/com/reelshort/backend/auth/PasswordHasher.java`
- Create: `backend/src/main/java/com/reelshort/backend/auth/BCryptPasswordHasher.java`
- Create: `backend/src/main/java/com/reelshort/backend/auth/TokenService.java`
- Create: `backend/src/main/java/com/reelshort/backend/auth/OpaqueTokenService.java`
- Create: `backend/src/main/java/com/reelshort/backend/auth/AuthToken.java`
- Test: `backend/src/test/java/com/reelshort/backend/auth/AuthServiceTests.java`

**Steps:**
1. Write failing service tests for registration success, duplicate username, invalid password, disabled user, and no plaintext password storage.
2. Run targeted service tests and verify they fail.
3. Implement password hashing, token signing, and auth use cases.
4. Run targeted service tests and verify they pass.

### Task 4: App Auth API

**Files:**
- Create: `backend/src/main/java/com/reelshort/backend/auth/AuthController.java`
- Create: `backend/src/main/java/com/reelshort/backend/auth/RegisterRequest.java`
- Create: `backend/src/main/java/com/reelshort/backend/auth/LoginRequest.java`
- Modify: `backend/src/test/java/com/reelshort/backend/auth/AuthControllerTests.java`

**Steps:**
1. Complete controller tests for successful register/login and validation errors.
2. Run targeted controller tests and verify they fail.
3. Implement request records and controller endpoints under `/api/app/auth`.
4. Run targeted controller tests and verify they pass.

### Task 5: Docs, Review, and Merge

**Files:**
- Modify: `AGENTS.md`
- Modify: `README.md`
- Create: `docs/api/auth-user.md`

**Steps:**
1. Update API docs and project stage notes.
2. Run `.\gradlew.bat --no-daemon --console=plain test --rerun-tasks`.
3. Run `git diff --check master`.
4. Review `master` diff for obvious design, security, and API issues.
5. Fix review findings with tests first where behavior changes.
6. Commit on feature branch, merge back to `master`, rerun verification on `master`, then remove worktree.
