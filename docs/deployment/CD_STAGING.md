# Continuous deployment — staging backend

Every push to `develop` that touches `backend/**` runs [deploy-staging.yml](../../.github/workflows/deploy-staging.yml):

1. **test**: `mvnw test` on the merged commit (Backend CI only runs on pull requests).
2. **image**: builds `backend/Dockerfile`, pushes `ghcr.io/zaabinh/finance-manager-backend:<sha>` with label
   `org.opencontainers.image.revision=<sha>`.
3. **deploy** (GitHub environment `staging`): SSH as `deploy` and run `sudo /srv/dong/deploy.sh <sha>`, then check
   public readiness through Cloudflare.

[deploy.sh](../../deploy/staging/deploy.sh) pulls the image, refuses it if the revision label differs, retags it to the
local name `compose.yaml` already uses (`finance-manager-backend:${BACKEND_TAG}`), switches `BACKEND_TAG` in `.env`,
recreates **only** the backend and waits up to 180 s for `/actuator/health/readiness` (checked from the Caddy
container). On failure it restores the previous `BACKEND_TAG`. Every run is logged to `/srv/dong/releases/deploy.log`.
`compose.yaml`, Caddy, PostgreSQL, Redis and the secrets are not touched. Database migrations run on backend start
(Flyway), so a failed migration also triggers the rollback of the image, not of the schema.

It can also be started by hand: GitHub → Actions → Deploy Staging → Run workflow.

## One-time setup

The automation cannot grant itself access; these steps are done by an admin.

### 1. Authorize the CI key for `deploy`

The CI key pair was generated on the admin machine: `~/.ssh/fm_staging_ci` (private) and `~/.ssh/fm_staging_ci.pub`
(fingerprint `SHA256:FkOd3U3m2Zo1sHSC6QhNQJNCymbslJ/HDBmchvssZgo`). Add it with forwarding disabled:

```bash
# On the VPS as deploy
umask 077; mkdir -p ~/.ssh
echo 'restrict ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGm9b0DADBGO+tafoKgOgnQtDYy8l15jy2FBWM58pwO/ github-actions-deploy-staging' >> ~/.ssh/authorized_keys
```

`restrict` disables port/agent/X11 forwarding and PTY for this key. The sudoers rule in step 2 limits what it can run.

### 2. Install the script root-owned and allow only it through sudo

Install from a reviewed checkout of the commit that contains it (root-owned so `deploy` cannot edit what sudo runs):

```bash
cd /srv/dong/source && git fetch origin && git checkout --detach <reviewed-sha>
sudo install -o root -g root -m 0755 deploy/staging/deploy.sh /srv/dong/deploy.sh
echo 'deploy ALL=(root) NOPASSWD: /srv/dong/deploy.sh' | sudo tee /etc/sudoers.d/dong-deploy >/dev/null
sudo chmod 0440 /etc/sudoers.d/dong-deploy && sudo visudo -cf /etc/sudoers.d/dong-deploy
sudo -l -U deploy   # must list only /srv/dong/deploy.sh as NOPASSWD
```

`deploy.sh` accepts nothing but a 40-character hex SHA. Re-install it the same way whenever the file changes.

### 3. Let the server pull the image

After the first successful **image** job, GitHub creates the package `finance-manager-backend` as **private**.
Either make it public (GitHub → Packages → finance-manager-backend → Package settings → Change visibility → Public;
the repository is public anyway), or log in once as root with a read-only token:

```bash
sudo docker login ghcr.io -u zaabinh   # password: classic PAT with read:packages only
```

### 4. GitHub environment `staging`

GitHub → Settings → Environments → New environment `staging` (optionally add required reviewers or restrict to
`develop`). Then, from the admin machine:

```bash
R=zaabinh/finance-manager-android
printf '%s' 139.180.156.175 | gh secret set SSH_HOST --env staging -R $R
printf '%s' deploy          | gh secret set SSH_USER --env staging -R $R
gh secret set SSH_PRIVATE_KEY --env staging -R $R < ~/.ssh/fm_staging_ci
ssh-keyscan -t ed25519 139.180.156.175 | gh secret set SSH_KNOWN_HOSTS --env staging -R $R
```

Verify the host key fingerprint in the Vultr console before trusting it:
`SHA256:a3ryhgGmYtQ0AjPBfgdBaL3ScqZ6efJ4A964rsDXJJ8` (ED25519, as seen on 2026-11-10).

### 5. SSH reachability from GitHub runners

GitHub-hosted runners connect from changing IP ranges. If the Vultr firewall only allows port 22 from the admin IP,
the deploy job cannot connect. Choose one:

| Option | Inbound port 22 | Notes |
| --- | --- | --- |
| Tailscale (`tailscale/github-action`) + Tailscale on the VPS | Closed to the Internet | Recommended; needs an auth key secret |
| Self-hosted runner on the VPS | None | Simplest network-wise; workflow code then runs on the server |
| Allow GitHub Actions IP ranges (`api.github.com/meta`) | Open to many IPs | Large, changing list; least preferred |

## Operating

- Log: `sudo tail -n 50 /srv/dong/releases/deploy.log`.
- Manual rollback: `sudo /srv/dong/deploy.sh <previous-sha>` (the image is still in GHCR and locally).
- A failed deploy leaves the previous version running and fails the workflow; check the job log and `deploy.log`.
- Production should reuse the same script with its own environment, triggered from `main` or a release tag behind
  required reviewers. Not set up yet.
