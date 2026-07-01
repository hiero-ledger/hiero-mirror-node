#!/usr/bin/env bash

# SPDX-License-Identifier: Apache-2.0

set -o pipefail

# ---- static config --------------------------------------------------------

GCS_ENDPOINT="https://storage.googleapis.com"
GCS_USER_PROJECT_HEADER="x-amz-user-project"

# network -> bucket name (shared across AWS and GCS)
declare -A BUCKETS=(
  [mainnet]="hedera-mainnet-recent-block-streams"
  [testnet]="hedera-testnet-recent-block-streams"
  [previewnet]="hedera-previewnet-recent-block-streams"
)

# ---- defaults / state -----------------------------------------------------

PROVIDER=""
NETWORK=""
ACCESS_KEY=""
SECRET_KEY=""
AWS_REGION_ARG=""
GCS_BILLING_PROJECT=""
USE_RANGE=1
VERBOSE=0

FAIL_COUNT=0
RESULTS=()   # "network|provider|op|status|detail"

# ---- usage ----------------------------------------------------------------

usage() {
  cat <<'EOF'
Usage: verify-block-streams-bucket-access.sh [options]

Verifies LIST and GET (requester-pays) access to a chosen Hedera recent-block-
stream bucket (mainnet, testnet or previewnet), on AWS or GCS.

Options:
  -p, --provider <aws|gcs>        Which cloud to check           (required)
  -n, --network  <name>           mainnet|testnet|previewnet     (required)
      --access-key <key>          Access key id / HMAC key   (required)
      --secret-key <secret>       Secret key / HMAC secret   (required)
      --region <region>           AWS region for the bucket (if needed)
      --project <gcp-project-id>  GCS billing project (required for gcs)
      --no-range                  Do a full GET instead of a bytes=0-0 ranged GET
  -v, --verbose                   Print the operation/target for each check
  -h, --help                      Show this help

Explicit access key and secret key are always required for the chosen provider,
supplied via the flags above. Note that command-line secrets are visible in the
process list on shared hosts. The script passes credentials to aws/awscurl via
the environment internally (never on their argv).

Examples:
  # AWS, testnet
  ./verify-block-streams-bucket-access.sh -p aws -n testnet \
      --access-key AKIA... --secret-key ...

  # GCS, previewnet
  ./verify-block-streams-bucket-access.sh -p gcs -n previewnet --project my-gcp-project \
      --access-key GOOG... --secret-key ...
EOF
}

log()  { printf '%s\n' "$*" >&2; }
vlog() { (( VERBOSE )) && printf '%s\n' "$*" >&2; }
die()  { printf 'Error: %s\n' "$*" >&2; exit 2; }

record() { # network provider op status detail
  RESULTS+=("$1|$2|$3|$4|$5")
  [[ "$4" == "FAIL" ]] && ((FAIL_COUNT++))
  printf '  [%-4s] %-10s %-4s %-4s  %s\n' "$4" "$1" "$2" "$3" "$5"
}

# ---- arg parsing ----------------------------------------------------------

while [[ $# -gt 0 ]]; do
  case "$1" in
    -p|--provider)    PROVIDER="${2:-}"; shift 2 ;;
    -n|--network)     NETWORK="${2:-}"; shift 2 ;;
    --access-key)     ACCESS_KEY="${2:-}"; shift 2 ;;
    --secret-key)     SECRET_KEY="${2:-}"; shift 2 ;;
    --region)         AWS_REGION_ARG="${2:-}"; shift 2 ;;
    --project)        GCS_BILLING_PROJECT="${2:-}"; shift 2 ;;
    --no-range)       USE_RANGE=0; shift ;;
    -v|--verbose)     VERBOSE=1; shift ;;
    -h|--help)        usage; exit 0 ;;
    *) die "unknown argument: $1 (use --help)" ;;
  esac
done

case "$PROVIDER" in
  aws|gcs) ;;
  "") die "a provider is required: choose aws or gcs (-p)" ;;
  *) die "--provider must be aws or gcs" ;;
esac

if [[ -z "$NETWORK" ]]; then
  die "a network is required: choose one of mainnet, testnet, previewnet (-n)"
elif [[ -n "${BUCKETS[$NETWORK]:-}" ]]; then
  SELECTED_NETWORK="$NETWORK"
else
  die "--network must be one of: mainnet, testnet, previewnet"
fi

want_aws() { [[ "$PROVIDER" == "aws" ]]; }
want_gcs() { [[ "$PROVIDER" == "gcs" ]]; }

# ---- dependency / credential preflight ------------------------------------

need() { command -v "$1" >/dev/null 2>&1 || die "required tool not found: $1"; }

if want_aws; then
  need aws
fi

if want_gcs; then
  need awscurl
  need xmllint
  [[ -n "$GCS_BILLING_PROJECT" ]] || \
    die "GCS billing project required (use --project)"
fi

[[ -n "$ACCESS_KEY" && -n "$SECRET_KEY" ]] || \
  die "access key and secret key required (use --access-key and --secret-key)"

# ---- temp workspace -------------------------------------------------------

TMPDIR_RUN="$(mktemp -d "${TMPDIR:-/tmp}/blkverify.XXXXXX")"
cleanup() { rm -rf "$TMPDIR_RUN"; }
trap cleanup EXIT
OUT="$TMPDIR_RUN/out"
ERR="$TMPDIR_RUN/err"

# first non-empty line of stderr, for compact error reporting
err_summary() {
  local line
  line="$(grep -v '^[[:space:]]*$' "$ERR" 2>/dev/null | tail -n 1)"
  [[ -n "$line" ]] && printf '%s' "$line"
}

# ---- AWS checks -----------------------------------------------------------

aws_base_opts() {
  local -a opts=(--request-payer requester)
  [[ -n "$AWS_REGION_ARG" ]] && opts+=(--region "$AWS_REGION_ARG")
  printf '%s\n' "${opts[@]}"
}

# Run the aws CLI with explicit credentials injected via the environment
# (not argv). AWS_PROFILE is unset so the explicit keys are always used.
aws_cli() {
  env -u AWS_PROFILE \
      "AWS_ACCESS_KEY_ID=$ACCESS_KEY" \
      "AWS_SECRET_ACCESS_KEY=$SECRET_KEY" \
      aws "$@"
}

check_aws() {
  local net="$1" bucket="$2" key=""
  local -a opts=()
  while IFS= read -r o; do opts+=("$o"); done < <(aws_base_opts)

  # --- LIST ---
  vlog "aws s3api list-objects-v2 --bucket $bucket --max-keys 1 (requester pays)"
  key="$(aws_cli s3api list-objects-v2 \
            --bucket "$bucket" --max-keys 1 \
            "${opts[@]}" \
            --query 'Contents[0].Key' --output text 2>"$ERR")"
  local rc=$?
  if [[ $rc -ne 0 ]]; then
    record "$net" aws list FAIL "$(err_summary)"
    return
  fi
  if [[ -z "$key" || "$key" == "None" ]]; then
    record "$net" aws list PASS "bucket empty (no objects returned)"
    record "$net" aws get  SKIP "no object available to read"
    return
  fi
  record "$net" aws list PASS "first key: $key"

  # --- GET (first object returned by list) ---
  local target="$key"
  local -a getopts=("${opts[@]}")
  (( USE_RANGE )) && getopts+=(--range "bytes=0-0")
  vlog "aws s3api get-object --bucket $bucket --key $target (requester pays$( ((USE_RANGE)) && echo ', ranged' ))"
  aws_cli s3api get-object \
        --bucket "$bucket" --key "$target" \
        "${getopts[@]}" \
        "$OUT" >/dev/null 2>"$ERR"
  rc=$?
  if [[ $rc -eq 0 ]]; then
    # A successful GET confirms read access, even when the object has 0 bytes.
    if [[ -s "$OUT" ]]; then
      record "$net" aws get PASS "read $target"
    else
      record "$net" aws get PASS "read $target (empty object)"
    fi
  else
    # A ranged read of a 0-byte object is rejected with InvalidRange (HTTP 416);
    # that still proves access (a permission failure returns 403, not 416). The
    # aws CLI prints a multi-line error, so scan the whole stderr, not just the
    # summary line.
    if grep -qiE 'InvalidRange|not satisfiable|ActualObjectSize|\b416\b' "$ERR"; then
      record "$net" aws get PASS "read $target (empty object, range not satisfiable)"
    else
      record "$net" aws get FAIL "$(err_summary)"
    fi
  fi
  rm -f "$OUT"
}

# ---- GCS checks -----------------------------------------------------------

# Run awscurl against GCS. stdout -> $1, stderr -> $2, extra args follow.
# Credentials are passed via the environment (not argv) and AWS_PROFILE is
# unset so the explicit HMAC key is always used.
gcs_awscurl() {
  local outf="$1" errf="$2"; shift 2
  env -u AWS_PROFILE \
      "AWS_ACCESS_KEY_ID=$ACCESS_KEY" \
      "AWS_SECRET_ACCESS_KEY=$SECRET_KEY" \
      awscurl \
      --service s3 --region auto \
      -H "${GCS_USER_PROJECT_HEADER}: ${GCS_BILLING_PROJECT}" \
      "$@" >"$outf" 2>"$errf"
}

# Is the response body an S3/GCS XML error document?
is_xml_error() {
  grep -q "<Error>" "$1" 2>/dev/null && grep -q "<Code>" "$1" 2>/dev/null
}
xml_error_text() {
  local code msg
  code="$(xmllint --xpath "string(/*[local-name()='Error']/*[local-name()='Code'])" "$1" 2>/dev/null)"
  msg="$(xmllint  --xpath "string(/*[local-name()='Error']/*[local-name()='Message'])" "$1" 2>/dev/null)"
  printf '%s' "${code:-error}${msg:+: $msg}"
}

check_gcs() {
  local net="$1" bucket="$2" key=""
  local listout="$TMPDIR_RUN/list.xml"

  # --- LIST ---
  vlog "GET $GCS_ENDPOINT/$bucket?list-type=2&max-keys=1 (x-amz-user-project: $GCS_BILLING_PROJECT)"
  gcs_awscurl "$listout" "$ERR" \
      "${GCS_ENDPOINT}/${bucket}?list-type=2&max-keys=1"
  local rc=$?
  if [[ $rc -ne 0 ]]; then
    record "$net" gcs list FAIL "awscurl rc=$rc${ERR:+; $(err_summary)}"
    return
  fi
  if is_xml_error "$listout"; then
    record "$net" gcs list FAIL "$(xml_error_text "$listout")"
    return
  fi
  if ! grep -q "ListBucketResult" "$listout"; then
    record "$net" gcs list FAIL "unexpected response (no ListBucketResult)"
    return
  fi
  key="$(xmllint --xpath "string((//*[local-name()='Contents']/*[local-name()='Key'])[1])" "$listout" 2>/dev/null)"
  if [[ -z "$key" ]]; then
    record "$net" gcs list PASS "bucket empty (no objects returned)"
    record "$net" gcs get  SKIP "no object available to read"
    return
  fi
  record "$net" gcs list PASS "first key: $key"

  # --- GET (first object returned by list) ---
  local target="$key"
  local -a rangehdr=()
  (( USE_RANGE )) && rangehdr=(-H "Range: bytes=0-0")
  vlog "GET $GCS_ENDPOINT/$bucket/$target$( ((USE_RANGE)) && echo ' (ranged)' )"
  gcs_awscurl "$OUT" "$ERR" "${rangehdr[@]}" "${GCS_ENDPOINT}/${bucket}/${target}"
  rc=$?
  if [[ $rc -ne 0 ]]; then
    record "$net" gcs get FAIL "awscurl rc=$rc${ERR:+; $(err_summary)}"
  elif is_xml_error "$OUT"; then
    # A ranged read of a 0-byte object is rejected with InvalidRange (HTTP 416);
    # that still proves access (a permission failure returns 403, not 416).
    if grep -qiE 'InvalidRange|not satisfiable|ActualObjectSize|\b416\b' "$OUT"; then
      record "$net" gcs get PASS "read $target (empty object, range not satisfiable)"
    else
      record "$net" gcs get FAIL "$(xml_error_text "$OUT")"
    fi
  elif [[ -s "$OUT" ]]; then
    record "$net" gcs get PASS "read $target"
  else
    # An empty body with no XML error means the object was read and has 0 bytes.
    record "$net" gcs get PASS "read $target (empty object)"
  fi
  rm -f "$OUT"
}

# ---- run ------------------------------------------------------------------

printf 'Verifying requester-pays access  provider=%s  network=%s%s\n' \
  "$PROVIDER" "$SELECTED_NETWORK" \
  "$( ((USE_RANGE)) && echo '  (ranged get)' )"
log ""

net="$SELECTED_NETWORK"
bucket="${BUCKETS[$net]}"
printf '%s  (%s)\n' "$net" "$bucket"
if want_aws; then check_aws "$net" "$bucket"; fi
if want_gcs; then check_gcs "$net" "$bucket"; fi
log ""

# ---- summary --------------------------------------------------------------

printf 'Summary\n'
pass=0; fail=0; skip=0
for r in "${RESULTS[@]}"; do
  IFS='|' read -r _n _p _o status _d <<<"$r"
  case "$status" in PASS) ((pass++));; FAIL) ((fail++));; SKIP) ((skip++));; esac
done
printf '  PASS=%d  FAIL=%d  SKIP=%d\n' "$pass" "$fail" "$skip"

if (( FAIL_COUNT > 0 )); then
  printf 'One or more checks failed.\n'
  exit 1
fi
printf 'All checks passed.\n'
exit 0
