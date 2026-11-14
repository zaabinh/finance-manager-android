# Continuous deployment — staging backend

This branch prepares CD code. Pushing it does not install the script, configure secrets, merge to `develop`,
or deploy the server. An admin must complete the setup below before the first deployment.

Every push to `develop` that touches `backend/**`, `deploy/staging/**` or `.github/workflows/deploy-staging.yml`
runs [deploy-staging.yml](../../.github/workflows/deploy-staging.yml). Android-only changes do not deploy:

1. **test**: Bash syntax and deployment regression tests, then `mvnw test` on the merged commit
   (Backend CI only runs on pull requests).
2. **image**: builds `backend/Dockerfile`, pushes `ghcr.io/zaabinh/finance-manager-backend:<sha>` with label
   `org.opencontainers.image.revision=<sha>`. Both use the full 40-character commit SHA.
3. **deploy** (GitHub environment `staging`): SSH as `deploy` and run `sudo /usr/local/sbin/dong-deploy-staging <sha>`, then check
   public readiness through Cloudflare.

[deploy.sh](../../deploy/staging/deploy.sh) uses the existing `/srv/dong/compose.yaml`, not the local development
Compose files. It requires exactly one canonical `BACKEND_TAG=<tag>` line in `.env`, a valid Docker tag and the
previous image available locally. Legacy local tags are supported as automatic rollback targets. It pulls the
new full-SHA image, verifies its revision label before editing `.env`, and retags it as
`finance-manager-backend:${BACKEND_TAG}`. It recreates **only** `backend` and polls readiness from the existing
`caddy` container with a 180-second sleep budget; request execution time adds to that budget.

Failures before switching leave the tag untouched. Failures after switching, including Compose startup and
internal readiness failures, attempt to restore the previous tag, recreate the backend and check readiness again.
The workflow still fails after rollback; rollback can itself fail and require manual intervention. The later
public check is outside this transaction: failure fails the workflow but leaves the internally ready version
running. Investigate DNS/proxy/connectivity and use manual rollback if needed.

Every run is logged to `/srv/dong/releases/deploy.log`. This single-backend replacement can interrupt requests;
it does not provide zero downtime. The script does not change Compose, Caddy, PostgreSQL or Redis configuration.
Flyway runs on backend startup: image rollback does **not** undo schema changes. Back up the database before new
migrations and keep them compatible with the previous application image.

All deployment jobs require `refs/heads/develop`, including manual runs. The separate
[deployment checks](../../.github/workflows/deploy-staging-checks.yml) run Bash syntax and mock-Docker regression
tests on pushes and PRs changing deployment code. They use no staging secrets and do not deploy.

It can also be started by hand: GitHub → Actions → Deploy Staging → Run workflow, selecting `develop`.
GitHub requires a manually dispatched workflow to be present on the default branch before it is discoverable.
The default branch is currently `main`; pushing this feature branch alone does not enable the Run workflow UI
or API dispatch.

## One-time setup

The automation cannot grant itself access; these steps are done by an admin. They are instructions, not evidence
that the server or GitHub configuration has been completed.

### 1. Authorize the CI key for `deploy`

The CI key pair was generated on the admin machine: `~/.ssh/fm_staging_ci` (private) and `~/.ssh/fm_staging_ci.pub`
(fingerprint `SHA256:FkOd3U3m2Zo1sHSC6QhNQJNCymbslJ/HDBmchvssZgo`). Add it with forwarding disabled:

```bash
# On the VPS as deploy
umask 077; mkdir -p ~/.ssh
echo 'restrict ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGm9b0DADBGO+tafoKgOgnQtDYy8l15jy2FBWM58pwO/ github-actions-deploy-staging' >> ~/.ssh/authorized_keys
```

`restrict` disables port/agent/X11 forwarding and PTY for this key. It still permits a shell. The `deploy` account
must not belong to the `docker` group or retain broader sudo privileges; Docker access is equivalent to root.

### 2. Install the script root-owned and allow only it through sudo

Root ownership of a script inside a directory writable by `deploy` is insufficient: the account could replace it.
Install outside `/srv/dong`, and protect all inputs consumed by root's Docker Compose. Review the server layout
before applying these commands during an admin maintenance window. Do not recursively change data-volume ownership.

```bash
cd <admin-owned-reviewed-checkout>
git fetch origin && git checkout --detach <reviewed-sha>
sudo install -o root -g root -m 0755 deploy/staging/deploy.sh /usr/local/sbin/dong-deploy-staging
sudo chown root:root /srv/dong /srv/dong/.env /srv/dong/compose.yaml
sudo chmod 0755 /srv/dong
sudo chmod 0600 /srv/dong/.env /srv/dong/compose.yaml
sudo install -d -o root -g root -m 0700 /srv/dong/releases
sudo namei -l /usr/local/sbin/dong-deploy-staging /srv/dong/.env /srv/dong/compose.yaml
echo 'deploy ALL=(root) NOPASSWD: /usr/local/sbin/dong-deploy-staging' | sudo tee /etc/sudoers.d/dong-deploy >/dev/null
sudo chmod 0440 /etc/sudoers.d/dong-deploy && sudo visudo -cf /etc/sudoers.d/dong-deploy
sudo -l -U deploy   # must list only /usr/local/sbin/dong-deploy-staging as NOPASSWD
```

Check every parent directory is root-owned and not writable by `deploy`, including through ACLs. Apply the same
restriction to any Compose include, `env_file`, secret/config file and its parent directories referenced by
`compose.yaml`. Existing `releases` contents must also be root-owned and protected. Remove the old
`/srv/dong/deploy.sh` sudoers rule if present; the account must have no other sudo access.

`deploy.sh` accepts exactly one 40-character lowercase hex SHA. Re-install it the same way whenever the file changes;
the workflow does not update privileged server files. Before enabling CD, verify the current tag's local image
exists with `sudo docker image inspect finance-manager-backend:<current-tag>` and keep it available for rollback.

### 3. Let the server pull the image

After the first successful **image** job, GitHub creates the package `finance-manager-backend` as **private**.
Either deliberately make the package public, or log in once as root with a read-only token that has access to it:

```bash
sudo docker login ghcr.io -u zaabinh   # password: classic PAT with read:packages only
```

### 4. GitHub environment `staging`

GitHub → Settings → Environments → New environment `staging`, restrict deployment branches to `develop`, and
optionally add required reviewers. Then, from the admin machine:

```bash
R=zaabinh/finance-manager-android
printf '%s' 139.180.156.175 | gh secret set SSH_HOST --env staging -R $R
printf '%s' deploy          | gh secret set SSH_USER --env staging -R $R
gh secret set SSH_PRIVATE_KEY --env staging -R $R < ~/.ssh/fm_staging_ci
# Verify the scanned host key against the Vultr console before storing it.
ssh-keyscan -t ed25519 139.180.156.175 | gh secret set SSH_KNOWN_HOSTS --env staging -R $R
```

Verify the host key fingerprint in the Vultr console before trusting it:
`SHA256:a3ryhgGmYtQ0AjPBfgdBaL3ScqZ6efJ4A964rsDXJJ8` (ED25519, as seen on 2026-11-10).

### 5. SSH reachability from GitHub runners

GitHub-hosted runners connect from changing IP ranges. If the Vultr firewall only allows port 22 from the admin IP,
the deploy job cannot connect. Confirm an existing firewall policy permits runner access; do not disable it to
make a run pass. A private tunnel or self-hosted runner would require a separate reviewed workflow/server change;
neither is configured by this branch.

## Operating

- Log: `sudo tail -n 50 /srv/dong/releases/deploy.log`.
- Manual image rollback: `sudo /usr/local/sbin/dong-deploy-staging <previous-full-sha>`. The image must still exist
  in GHCR; confirm schema compatibility first. A pre-CD legacy tag is only an automatic rollback target.
- An internal deployment failure attempts rollback; check the log to confirm whether the previous version is ready.
- A failed public check does not automatically roll back.
- Production should reuse the same script with its own environment, triggered from `main` or a release tag behind
  required reviewers. Not set up yet.

Local tests use fake Docker commands and do not verify real images, SSH, server ownership, environment secrets,
database migrations, public readiness or a hosted Actions run. Confirm those during the first authorized staging run.

## Branch verification (2026-11-14)

Prepared on `origin/develop@824898e` in an isolated worktree, preserving unrelated local edits.
Independent review found no remaining blocking issues. Local checks actually executed:

- `bash -n deploy/staging/deploy.sh`: pass.
- `python -m unittest discover -s deploy/staging/tests -v`: 12 tests pass, including startup/readiness rollback,
  rollback failure reporting, ambiguous dotenv keys, image validation and locking; fake Docker only.
- `mvnw.cmd -B -ntp test` with JDK 21: 170 tests, no failures/errors/skips.
- actionlint 1.7.12 on both deployment workflows: pass; ShellCheck was not installed and was disabled.
- `git diff --check`: pass.

The feature branch is intended for review/merge into `develop`; pushing it runs only secret-free deployment
checks. It does not install the privileged script or perform the first server deployment.
