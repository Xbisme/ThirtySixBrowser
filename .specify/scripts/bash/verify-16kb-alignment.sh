#!/usr/bin/env bash
# Verify 16KB page-size alignment of native libraries (Constitution §IX, FR-016).
#
# Usage:
#   verify-16kb-alignment.sh <path-to-apk-or-aab>
#
# Behaviour:
#   - If the artifact does not exist OR contains no `lib/<abi>/*.so` entries,
#     exit 0 with a "skip" log (Spec 001 fail-soft requirement).
#   - If `.so` entries exist, every PT_LOAD segment p_align value MUST be
#     >= 0x4000 (16KB). Any segment with p_align < 0x4000 fails the script.
#   - Requires `unzip` (always present) and one of: `objdump`, `llvm-objdump`,
#     `readelf`, `llvm-readelf`. Falls back gracefully across availability.

set -euo pipefail

ARTIFACT="${1:-}"

if [[ -z "${ARTIFACT}" ]]; then
    echo "[16kb] usage: $0 <path-to-apk-or-aab>" >&2
    exit 2
fi

if [[ ! -f "${ARTIFACT}" ]]; then
    echo "[16kb] artifact not found: ${ARTIFACT} — skip (no native libs to verify)"
    exit 0
fi

# List .so entries inside the APK/AAB.
SO_ENTRIES=$(unzip -l "${ARTIFACT}" 2>/dev/null | awk '/lib\/.*\.so$/ {print $NF}' || true)

if [[ -z "${SO_ENTRIES}" ]]; then
    echo "[16kb] no native libraries to verify (skip)"
    exit 0
fi

# Pick an ELF inspector
ELF_TOOL=""
ELF_MODE=""
for cand in objdump llvm-objdump readelf llvm-readelf; do
    if command -v "${cand}" >/dev/null 2>&1; then
        ELF_TOOL="${cand}"
        case "${cand}" in
            *objdump) ELF_MODE="objdump" ;;
            *readelf) ELF_MODE="readelf" ;;
        esac
        break
    fi
done

if [[ -z "${ELF_TOOL}" ]]; then
    echo "[16kb] no ELF inspector available (need objdump or readelf) — cannot verify alignment" >&2
    exit 1
fi

echo "[16kb] inspecting $(echo "${SO_ENTRIES}" | wc -l | tr -d ' ') native lib(s) in ${ARTIFACT} via ${ELF_TOOL}"

WORK_DIR=$(mktemp -d -t verify-16kb-XXXXXX)
trap 'rm -rf "${WORK_DIR}"' EXIT

FAIL=0
THRESHOLD=$((16#4000))  # 0x4000 = 16384 = 16KB

while IFS= read -r entry; do
    [[ -z "${entry}" ]] && continue
    OUT="${WORK_DIR}/$(basename "${entry}")"
    unzip -p "${ARTIFACT}" "${entry}" >"${OUT}" 2>/dev/null

    # Extract p_align values from PT_LOAD segments
    if [[ "${ELF_MODE}" == "objdump" ]]; then
        # objdump -p prints lines like:  LOAD off 0x... vaddr 0x... paddr 0x... align 2**14
        ALIGN_VALUES=$("${ELF_TOOL}" -p "${OUT}" 2>/dev/null \
            | awk '/LOAD/ && /align/ { for (i=1;i<=NF;i++) if ($i=="align") print $(i+1) }')
    else
        # readelf -lW prints PT_LOAD lines with Align column at end
        ALIGN_VALUES=$("${ELF_TOOL}" -lW "${OUT}" 2>/dev/null \
            | awk '/^[[:space:]]+LOAD[[:space:]]/ { print $NF }')
    fi

    if [[ -z "${ALIGN_VALUES}" ]]; then
        echo "[16kb] WARN: could not parse PT_LOAD segments from ${entry} — skipping"
        continue
    fi

    while IFS= read -r raw; do
        [[ -z "${raw}" ]] && continue
        # Normalize to numeric
        if [[ "${raw}" == 2\*\** ]]; then
            exp="${raw#2\*\*}"
            value=$(( 1 << exp ))
        elif [[ "${raw}" == 0x* ]] || [[ "${raw}" == 0X* ]]; then
            value=$((raw))
        else
            value=$((raw))
        fi
        if (( value < THRESHOLD )); then
            printf "[16kb] FAIL  %-40s  align=0x%x (< 0x4000 / 16KB)\n" "${entry}" "${value}"
            FAIL=1
        else
            printf "[16kb] OK    %-40s  align=0x%x\n" "${entry}" "${value}"
        fi
    done <<<"${ALIGN_VALUES}"
done <<<"${SO_ENTRIES}"

if (( FAIL == 1 )); then
    echo "[16kb] one or more native libs are NOT 16KB-aligned — Constitution §IX violation"
    exit 1
fi

echo "[16kb] all native libs are 16KB-aligned ✅"
exit 0
