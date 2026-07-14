# CI/CD — my-wallet (backend + frontend)

Real-time CI/CD for the two-service **my-wallet** app: every push builds, tests, publishes a
container image, and **automatically rolls it out to the cluster** — push → live.

## 1. Overview

The app is split across **two independent GitHub repositories**, each with its own
`.github/workflows/`:

| Service | Folder | Stack | Repository | Branch |
|---|---|---|---|---|
| Backend | `demo/` | Spring Boot **4.0.2**, **Java 21**, Maven wrapper | `github.com/Srinivas465-tech/wex-wallet-backend` | `master` |
| Frontend | `react-latest/` | React 19 + **Vite 8** + TypeScript | `github.com/Srinivas465-tech/wex-wallet-frontend` | `master` |

Runtime topology (the frontend proxies API calls to the backend; the backend talks to Postgres):

```
browser ──▶ frontend (nginx :80)
                 │  location /api/ ──▶ proxy_pass ──▶ backend (:8080)
                 │                                        │
                 └── serves the Vite SPA build            └──▶ PostgreSQL (Neon)
```

Both services ship as **multi-stage Docker images**:
- Backend → `eclipse-temurin:21-jre-alpine`, non-root `app` user, port **8080**.
- Frontend → `nginx:alpine` serving the Vite `dist/`, port **80**; `nginx.conf` proxies
  `/api/` → `http://backend:8080/`.

### The pipeline at a glance

```
                    ┌──────────── wex-wallet-backend ────────────┐
 push to master ──▶ │ ci.yml            → mvn verify + Postgres    │
                    │ deploy.yml        → build+push GHCR → rollout│──▶ cluster (backend)
                    └─────────────────────────────────────────────┘
                    ┌──────────── wex-wallet-frontend ───────────┐
 push to master ──▶ │ ci.yml            → npm ci + lint + build    │
                    │ deploy.yml        → build+push GHCR → rollout│──▶ cluster (frontend)
                    └─────────────────────────────────────────────┘
```

Registry is **GHCR (`ghcr.io`)** — it authenticates with the built-in `GITHUB_TOKEN`, so
there are no registry secrets to configure. Deployment images are pinned to the immutable
**`:sha` tag** so each push is a genuine rollout, not a `:latest` no-op.

> **Why a single `master` branch?** This project uses a branchless workflow — you push
> directly to one branch, no PRs. Because `git init` may name the first branch `master`
> (older) or `main` (newer), the workflows below trigger on **both**.

---

## 2. Backend CI — `.github/workflows/ci.yml` (in `wex-wallet-backend`)

The only test, `MyAppApplicationTests.contextLoads()`, boots the **entire** Spring context,
and `src/main/resources/application.yaml` reads the datasource from environment variables with
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

➡️ `mvn verify` **fails** unless CI supplies a reachable Postgres and those four vars, so the
job runs a **Postgres service container** and passes the vars through.

> ### Two different databases — do not mix them
>
> | | Database | Credentials | Set where |
> |---|---|---|---|
> | **CI (`ci.yml`)** | throwaway `postgres:16-alpine` service container | dummy `wallet/wallet/wallet` | inline in the workflow |
> | **Runtime (deploy)** | **Neon** production Postgres | the real `neondb_owner` credentials | `k8s/01-config.yaml` (ConfigMap + Secret) |
>
> CI only needs *a* reachable Postgres so `contextLoads()` can boot the Spring context — it
> uses a disposable container that is destroyed when the job ends. **Never point CI at Neon:**
> it runs on every push/PR, so the prod credentials would leak into CI config/logs, and
> `SPRING_JPA_HIBERNATE_DDL_AUTO=update` would let Hibernate **mutate the real Neon schema**
> and tests would read/write production data. The Neon values belong only at runtime, applied
> to the cluster by the deploy job — see §8.
>
> Note the Neon JDBC URL **must** end with `?sslmode=require` (Neon rejects non-SSL
> connections). `k8s/01-config.yaml` has this; make sure the root `.env` does too.

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

Add a **`.dockerignore`** to `demo/` so a stray `target/` or `.idea/` does not bloat the build
context:

```
target/
.idea/
.git/
*.iml
HELP.md
```

---

## 3. Backend deploy — `.github/workflows/deploy.yml` (in `wex-wallet-backend`)

Builds the image, pushes to GHCR, then **rolls it into the cluster** in the same run. Reuses
the existing `Dockerfile` unchanged (it already runs `mvn clean package -DskipTests`; tests
are covered by `ci.yml`).

```yaml
name: Deploy

on:
  push:
    branches: [main, master]
    tags: ['v*']

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}   # ghcr.io/srinivas465-tech/wex-wallet-backend

jobs:
  build-and-push:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    outputs:
      image: ${{ steps.meta.outputs.tags }}
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
            type=sha                      # immutable per-commit tag used for rollout
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

  # ── real-time rollout ──────────────────────────────────────────────
  deploy:
    needs: build-and-push
    runs-on: self-hosted          # runner on the machine hosting minikube/kind (see §5)
    if: github.ref == 'refs/heads/master' || github.ref == 'refs/heads/main'
    steps:
      - uses: actions/checkout@v4

      # ConfigMap + Secret must exist; safe to re-apply (idempotent).
      # NOTE: keep real credentials OUT of the repo — see §8.
      - name: Ensure config
        run: kubectl apply -f k8s/01-config.yaml

      - name: Roll out new image
        run: |
          IMAGE=ghcr.io/${{ github.repository }}:sha-${GITHUB_SHA::7}
          kubectl set image deployment/backend backend=$IMAGE
          kubectl rollout status deployment/backend --timeout=120s
```

> `docker/metadata-action`'s `type=sha` produces a `sha-<short>` tag; the rollout above
> references that exact tag so every push triggers a real rolling update.

---

## 4. Frontend CI + deploy (in `wex-wallet-frontend`)

**`.github/workflows/ci.yml`** — no service container needed; `npm run build` runs `tsc -b`
then `vite build`.

```yaml
name: CI

on:
  push:
  pull_request:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up Node 22
        uses: actions/setup-node@v4
        with:
          node-version: '22'
          cache: npm

      - run: npm ci
      - run: npm run lint
      - run: npm run build

      - name: Upload build
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: dist
          path: dist/
          if-no-files-found: ignore
```

**`.github/workflows/deploy.yml`** — identical GHCR + rollout pattern, building
`react-latest/Dockerfile` and rolling `deployment/frontend`:

```yaml
name: Deploy

on:
  push:
    branches: [main, master]
    tags: ['v*']

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}   # ghcr.io/srinivas465-tech/wex-wallet-frontend

jobs:
  build-and-push:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v4
      - uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - id: meta
        uses: docker/metadata-action@v5
        with:
          images: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}
          tags: |
            type=ref,event=branch
            type=semver,pattern={{version}}
            type=sha
            type=raw,value=latest,enable={{is_default_branch}}
      - uses: docker/build-push-action@v6
        with:
          context: .
          push: true
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
          cache-from: type=gha
          cache-to: type=gha,mode=max

  deploy:
    needs: build-and-push
    runs-on: self-hosted
    if: github.ref == 'refs/heads/master' || github.ref == 'refs/heads/main'
    steps:
      - name: Roll out new image
        run: |
          IMAGE=ghcr.io/${{ github.repository }}:sha-${GITHUB_SHA::7}
          kubectl set image deployment/frontend frontend=$IMAGE
          kubectl rollout status deployment/frontend --timeout=120s
```

Add a **`.dockerignore`** to `react-latest/`:

```
node_modules
dist
.git
*.log
```

---

## 5. Making the real-time rollout work (self-hosted runner)

The `k8s/` manifests target what is currently a **local** cluster (`my-wallet.local`, local
`my-backend:1.0` / `my-frontend:1.0` images). **GitHub's cloud runners cannot reach a local
cluster.** To get true push-to-live deployment, register a **self-hosted runner** on the
machine that runs minikube/kind — that is what `runs-on: self-hosted` above targets.

One-time setup, per repo (or shared across both):

1. GitHub → repo **Settings → Actions → Runners → New self-hosted runner** (Linux).
2. On the cluster host, follow the shown commands:
   ```bash
   ./config.sh --url https://github.com/Srinivas465-tech/wex-wallet-backend --token <TOKEN>
   ./run.sh            # or install as a service: sudo ./svc.sh install && sudo ./svc.sh start
   ```
3. Ensure `kubectl` on that host points at the local cluster (`kubectl config current-context`
   → your minikube/kind context) and the runner user can use it.
4. Point the k8s Deployments at GHCR instead of the local `my-*:1.0` tags so pulls resolve
   (edit `k8s/02-backend.yaml` / `k8s/03-frontend.yaml` `image:` to `ghcr.io/...`). If the
   GHCR packages are **private**, add an `imagePullSecret`:
   ```bash
   kubectl create secret docker-registry ghcr-cred \
     --docker-server=ghcr.io --docker-username=<gh-user> \
     --docker-password=<gh-PAT-with-read:packages>
   # then reference it under spec.template.spec.imagePullSecrets in each Deployment
   ```

### Alternative: remote/cloud cluster

If you move to EKS/GKE/AKS or a reachable k3s VPS, drop the self-hosted runner: run the
`deploy` job on `runs-on: ubuntu-latest`, load a kubeconfig from a repo secret, and run the
same `set image` + `rollout status`:

```yaml
  deploy:
    needs: build-and-push
    runs-on: ubuntu-latest
    steps:
      - name: Configure kubectl
        run: |
          mkdir -p ~/.kube
          echo "${{ secrets.KUBE_CONFIG }}" | base64 -d > ~/.kube/config
      - run: |
          kubectl set image deployment/backend \
            backend=ghcr.io/${{ github.repository }}:sha-${GITHUB_SHA::7}
          kubectl rollout status deployment/backend --timeout=120s
```

Add `KUBE_CONFIG` (base64 of a scoped kubeconfig) under **Settings → Secrets and variables →
Actions**.

---

## 6. docker-compose (local / dev)

The root `docker-compose.yml` currently **builds from source** (`build.context: ./demo` and
`./react-latest`). For a pull-based deploy of the published images, switch to `image:`:

```yaml
services:
  backend:
    image: ghcr.io/srinivas465-tech/wex-wallet-backend:latest
    environment:
      SPRING_DATASOURCE_URL: ${SPRING_DATASOURCE_URL}
      SPRING_DATASOURCE_USERNAME: ${SPRING_DATASOURCE_USERNAME}
      SPRING_DATASOURCE_PASSWORD: ${SPRING_DATASOURCE_PASSWORD}
      SPRING_JPA_HIBERNATE_DDL_AUTO: ${SPRING_JPA_HIBERNATE_DDL_AUTO}
    ports: ["8080:8080"]
  frontend:
    image: ghcr.io/srinivas465-tech/wex-wallet-frontend:latest
    depends_on: [backend]
    ports: ["5173:80"]
```

```bash
docker compose pull && docker compose up -d   # pulls newest images and restarts
```

The four `SPRING_*` values come from the root `.env` file (keep it untracked).

---

## 7. GitOps alternative (optional)

Instead of giving CI access to the cluster, a **pull-based** model has an in-cluster
controller (**Argo CD** or **Flux**) watch a manifests repo and reconcile automatically. CI's
job ends at "push image + bump the manifest tag"; the controller does the rollout. This avoids
exposing the cluster/kubeconfig to GitHub Actions and is the more scalable long-term option —
worth adopting if the local cluster becomes a shared/remote one.

---

## 8. ⚠️ Secrets — action required

`k8s/01-config.yaml` currently commits a **real Neon PostgreSQL password in plaintext** (its
own comment notes it was shared, and it has since been pasted into chat). Treat it as fully
compromised:

1. **Rotate it now** in the Neon console — regenerate the `neondb_owner` password.
2. **Stop committing it.** Create the Secret out-of-band instead of storing the value in the
   file:
   ```bash
   kubectl create secret generic backend-secret \
     --from-literal=SPRING_DATASOURCE_USERNAME=<user> \
     --from-literal=SPRING_DATASOURCE_PASSWORD=<new-password>
   ```
   or source it from a GitHub Actions secret in the deploy job. Keep only non-sensitive keys
   (the JDBC URL, `ddl-auto`) in the committed ConfigMap.
3. Keep the JDBC URL with **`?sslmode=require`** everywhere it appears (Neon rejects non-SSL
   connections) — including the root `.env`, which currently omits it.
4. The GHCR **push** needs no secret — it uses the built-in `GITHUB_TOKEN` with
   `packages: write`. Only cluster credentials (`KUBE_CONFIG`, or an `imagePullSecret` for
   private images) need to be stored as repo secrets.

---

## 9. Verification

**Backend — the exact command CI runs** (needs Docker + the Maven wrapper):

```bash
docker run -d --name pg -e POSTGRES_DB=wallet -e POSTGRES_USER=wallet \
  -e POSTGRES_PASSWORD=wallet -p 5432:5432 postgres:16-alpine

SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/wallet \
SPRING_DATASOURCE_USERNAME=wallet SPRING_DATASOURCE_PASSWORD=wallet \
SPRING_JPA_HIBERNATE_DDL_AUTO=update ./mvnw -B verify
```

Expect `MyAppApplicationTests.contextLoads` to pass and `BUILD SUCCESS`.

**Frontend:**

```bash
npm ci && npm run lint && npm run build   # produces dist/
```

**Images build locally:**

```bash
cd demo         && docker build -t wex-wallet-backend:test .
cd react-latest && docker build -t wex-wallet-frontend:test .
docker compose up --build          # full stack at http://localhost:5173
```

**After pushing to GitHub — the real-time path:**
- **CI** goes green in each repo.
- **Deploy** builds + pushes to GHCR; a `ghcr.io/srinivas465-tech/wex-wallet-*` image appears
  under each repo's **Packages**.
- The self-hosted `deploy` job runs `kubectl rollout status` and exits green; verify live:
  ```bash
  kubectl get pods           # new pods Running with the fresh sha tag
  kubectl rollout history deployment/backend
  curl -H "Host: my-wallet.local" http://<ingress-ip>/api/...   # serves the new build
  ```
