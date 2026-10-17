#!/bin/sh
# Registers the runner with gitlab.com and writes config/config.toml, if it isn't registered yet.
# gitlab-runner.service runs it before every start; it can also be run by hand, from /opt/gitlab-runner:
#   ./register.sh glrt-xxxxxxxx        (or with RUNNER_TOKEN set, as gitlab-runner.env does for the service)
# The token comes from GitLab: the project's Settings > CI/CD > Runners > New project runner.
# To register again (a new token), delete config/config.toml first.
#
# Jobs get the pi's Podman socket too (the first --docker-volumes), so they can build and push images
# with docker build/push. That gives a job jreynolds' access to the pi: keep the runner to your own projects.
# It's at the same path as on the pi, because the buildpacks builder (the web app's image) mounts
# DOCKER_HOST by its path, and Podman resolves that path on the pi.
#
# The jobs' environment (the --env lines) describes the pi, so it lives here rather than in .gitlab-ci.yml:
#   REGISTRY     the pi's registry (/opt/docker-reg), as its Podman, which does the pushing, sees it
#   DOCKER_HOST  that Podman socket, at the path the jobs have it
set -eu
cd "$(dirname "$0")"

if [ -f config/config.toml ]; then
    echo "already registered (config/config.toml)"
    exit 0
fi

token=${1:-${RUNNER_TOKEN:-}}
[ -n "$token" ] || { echo "usage: ./register.sh <runner token (glrt-...)>, or set RUNNER_TOKEN" >&2; exit 1; }
mkdir -p config

podman run --rm -v "$PWD/config:/etc/gitlab-runner" docker.io/gitlab/gitlab-runner:alpine-v19.5.0 register \
    --non-interactive \
    --url https://gitlab.com \
    --token "$token" \
    --name pi \
    --request-concurrency 2 \
    --executor docker \
    --docker-image alpine:3.22 \
    --docker-host unix:///var/run/docker.sock \
    --docker-pull-policy if-not-present \
    --docker-volumes /run/user/1000/podman/podman.sock:/run/user/1000/podman/podman.sock \
    --docker-volumes /cache \
    --env "REGISTRY=localhost:5000" \
    --env "DOCKER_HOST=unix:///run/user/1000/podman/podman.sock"

# the pi has 4 cores: two jobs at a time
sed -i 's/^concurrent = .*/concurrent = 2/' config/config.toml
chmod 600 config/config.toml
echo "registered"
