#!/usr/bin/env bash
# Shared GHCR image-ref helpers for deploy.sh (sourced; no side effects).

# True if $1 is a qualified ghcr.io/diegoyegros/{aratiri,aratiri-frontend,aratiri-admin}
# ref with a tag or @sha256: pin.
is_qualified_aratiri_ghcr_ref() {
  local ref="$1"
  case "${ref}" in
    ghcr.io/diegoyegros/aratiri:*|ghcr.io/diegoyegros/aratiri@sha256:*|\
    ghcr.io/diegoyegros/aratiri-frontend:*|ghcr.io/diegoyegros/aratiri-frontend@sha256:*|\
    ghcr.io/diegoyegros/aratiri-admin:*|ghcr.io/diegoyegros/aratiri-admin@sha256:*)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

# Basename of an image ref without registry path, tag, or digest.
# Handles host:port/name:tag (strip @digest first; strip :tag only on the
# final path segment so registry ports are not mistaken for tags).
aratiri_image_basename() {
  local ref="$1" name
  name="${ref%%@*}"
  if [[ "${name}" == */* ]]; then
    name="${name##*/}"
  fi
  name="${name%%:*}"
  printf '%s' "${name}"
}

# True if this image ref is one of the three Aratiri app packages (any registry/tag).
is_aratiri_app_image_ref() {
  case "$(aratiri_image_basename "$1")" in
    aratiri|aratiri-frontend|aratiri-admin) return 0 ;;
    *) return 1 ;;
  esac
}

# Extract bare image refs from a compose file (image: lines only; ignores comments).
compose_image_refs() {
  local compose_file="$1"
  awk '
    /^[[:space:]]*#/ { next }
    /^[[:space:]]*image:[[:space:]]*/ {
      line = $0
      sub(/^[[:space:]]*image:[[:space:]]*/, "", line)
      gsub(/["'\'']/, "", line)
      if (line != "") print line
    }
  ' "${compose_file}"
}

# Fail-closed: every Aratiri app image in compose must be a qualified GHCR ref.
# Prints one log-style line per violation on stdout; returns 1 if any violation.
assert_compose_aratiri_ghcr_images() {
  local compose_file="$1"
  local ref bad=0

  while IFS= read -r ref; do
    [ -n "${ref}" ] || continue
    if is_aratiri_app_image_ref "${ref}" && ! is_qualified_aratiri_ghcr_ref "${ref}"; then
      echo "unqualified Aratiri app image ref (require ghcr.io/diegoyegros/{aratiri,aratiri-frontend,aratiri-admin} tag or @sha256: pin): ${ref}"
      bad=1
    fi
  done < <(compose_image_refs "${compose_file}")

  return "${bad}"
}
