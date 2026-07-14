# CI/CD — my-wallet Spring Boot backend

## 1. Project summary

| Property | Value |
|---|---|
| Framework | Spring Boot **4.0.2** |
| Language / runtime | **Java 21** (Eclipse Temurin) |
| Build tool | Maven via wrapper (`./mvnw`, Maven 3.9.x) |
| Domain | Wallet REST API — JWT auth, Spring Security, Spring Data JPA |
| Database | **PostgreSQL** (runtime dependency) |
| Container | Existing multi-stage `Dockerfile` (jar → `eclipse-temurin:21-jre-alpine`, non-root `app` user, port 8080) |
| Git status | ⚠️ Not yet a git repository |

## 2. Critical constraint the pipeline must handle

The only test is `src/test/java/com/example/demo/MyAppApplicationTests.java` — a
`@SpringBootTest` `contextLoads()` that boots the **entire** Spring context.
`src/main/resources/application.yaml` reads the datasource from environment variables with
**no defaults**:

```yaml
spring:
  datasource:
    url:      ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: ${SPRING_JPA_HIBERNATE_DDL_AUTO}
    show-sql: true
```

➡️ `mvn verify` **fails** unless CI supplies a reachable Postgres and those four env vars.
The CI job therefore runs a **Postgres service container** and passes the vars through.

## 3. Pipeline design

This project uses a **branchless / single-branch workflow** — you push directly to one
branch, with no feature branches or pull requests. The pipeline is two GitHub Actions
workflows: one runs on every push to give fast feedback, and one publishes the image only
from the default branch (or version tags).

```
every push              ──▶  ci.yml             → compile + test against Postgres
push to main|master,    ──▶  docker-publish.yml  → build image + push to GHCR
  or tag v*
```

> Note: there is no `pull_request` trigger, since you don't open PRs. And because modern
> `git init` may name the first branch `master` (older) or `main` (newer), the publish
> workflow listens for **both**.

## 4. Workflow: `.github/workflows/ci.yml`

Build and test on every push and pull request.

```yaml
name: CI

on:
  push:
  pull_request:

jobs:
  build-and-test:
    runs-on: ubuntu-latest

    services:
      postgres:
        image: postgres:16-alpine
        env:
          POSTGRES_DB: wallet
          POSTGRES_USER: wallet
          POSTGRES_PASSWORD: wallet
        ports:
          - 5432:5432
        options: >-
          --health-cmd "pg_isready -U wallet"
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven

      - name: Build and test
        env:
          SPRING_DATASOURCE_URL: jdbc:postgresql://localhost:5432/wallet
          SPRING_DATASOURCE_USERNAME: wallet
          SPRING_DATASOURCE_PASSWORD: wallet
          SPRING_JPA_HIBERNATE_DDL_AUTO: update
        run: ./mvnw -B verify

      - name: Upload test reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: surefire-reports
          path: target/surefire-reports/
          if-no-files-found: ignore
```

## 5. Workflow: `.github/workflows/docker-publish.yml`

Build the Docker image and push it to the GitHub Container Registry (GHCR) on pushes to
`main` and on version tags. Reuses the existing `Dockerfile` unchanged (it already runs
`mvn clean package -DskipTests`; tests are covered by `ci.yml`).

```yaml
name: Docker Publish

on:
  push:
    branches: [main]
    tags: ['v*']

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}

jobs:
  build-and-push:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write

    steps:
      - uses: actions/checkout@v4

      - name: Log in to GHCR
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Derive image tags
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}
          tags: |
            type=ref,event=branch
            type=semver,pattern={{version}}
            type=sha
            type=raw,value=latest,enable={{is_default_branch}}

      - name: Build and push
        uses: docker/build-push-action@v6
        with:
          context: .
          push: true
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
          cache-from: type=gha
          cache-to: type=gha,mode=max
```

## 6. `.dockerignore` (recommended)

There is currently no `.dockerignore`. Add one so a stray local `target/` or `.idea/`
does not bloat the Docker build context:

```
target/
.idea/
.git/
*.iml
HELP.md
```

## 7. One-time git prerequisite

The folder is not a git repository yet. GHCR push uses the built-in `GITHUB_TOKEN`, so
**there are no secrets to configure** — just ensure the publish workflow has
`packages: write` permission (already set above).

```bash
git init && git add -A && git commit -m "Initial commit"
git remote add origin git@github.com:<owner>/<repo>.git
git push -u origin main
```

## 8. Verification

**Local dry-run of the exact command CI runs** (needs Docker + the Maven wrapper):

```bash
docker run -d --name pg -e POSTGRES_DB=wallet -e POSTGRES_USER=wallet \
  -e POSTGRES_PASSWORD=wallet -p 5432:5432 postgres:16-alpine

SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/wallet \
SPRING_DATASOURCE_USERNAME=wallet SPRING_DATASOURCE_PASSWORD=wallet \
SPRING_JPA_HIBERNATE_DDL_AUTO=update ./mvnw -B verify
```

Expect `MyAppApplicationTests.contextLoads` to pass and the build to end with
`BUILD SUCCESS`.

**Local image build:**

```bash
docker build -t my-wallet:test .
```

**After pushing to GitHub:**
- The **CI** workflow runs on the push/PR and goes green.
- The **Docker Publish** workflow runs on `main` (and `v*` tags) and a
  `ghcr.io/<owner>/<repo>:latest` image appears under the repository's **Packages**.
