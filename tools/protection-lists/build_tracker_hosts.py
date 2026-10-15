#!/usr/bin/env python3
"""Build app/src/main/assets/protection/tracker_hosts.txt.

Run by hand when the list is refreshed. Never run from Gradle, CI or the app:
the committed asset is what a release ships (Constitution §I).

Sources: EasyPrivacy + EasyList by The EasyList authors (https://easylist.to/),
used under the CC BY-SA 3.0 option of their dual licence. The output is a
modified, host-level subset and is itself CC BY-SA 3.0.

EasyList's ad-server section alone is ~47k hosts (~400 KB gzip), over the
size budget (research R9). Ad-server hosts are therefore kept only when their
domain appears in a popularity list (the Tranco top-1M CSV, `rank,domain`),
used at build time as a membership filter only: no rank or other Tranco data is
written to the asset. EasyPrivacy and EasyList's third-party section are kept
in full.

Usage:
    python3 -I tools/protection-lists/build_tracker_hosts.py --popularity top-1m.csv \
        [--easyprivacy FILE] [--easylist FILE]
Without --easyprivacy/--easylist the two lists are downloaded from easylist.to.
Without --popularity the ad-server section is left out entirely.
"""

import argparse
import datetime
import gzip
import re
import sys
import urllib.request
from pathlib import Path

EASYPRIVACY_URL = "https://easylist.to/easylist/easyprivacy.txt"
EASYLIST_URL = "https://easylist.to/easylist/easylist.txt"

# Sections whose `||host^` rules name whole tracking / ad-serving hosts.
# Site-specific, CNAME-uncloaking, popup, cosmetic and adult sections are skipped.
EASYPRIVACY_SECTIONS = {
    "easyprivacy/easyprivacy_trackingservers_general.txt",
    "easyprivacy/easyprivacy_trackingservers_thirdparty.txt",
    "easyprivacy/easyprivacy_trackingservers_mining.txt",
    "easyprivacy/easyprivacy_trackingservers_notifications.txt",
    "easyprivacy/easyprivacy_trackingservers.txt",
    "easyprivacy/easyprivacy_trackingservers_international.txt",
    "easyprivacy/easyprivacy_thirdparty.txt",
    "easyprivacy/easyprivacy_thirdparty_international.txt",
}
EASYLIST_SECTIONS = {"easylist/easylist_thirdparty.txt"}
EASYLIST_ADSERVER_SECTIONS = {"easylist/easylist_adservers.txt"}

# Hosts serving content the person chose to see (spec FR-004a, research R3).
# Mirrors TrackerMatcher's never-block set; kept out of the asset as well.
NEVER_BLOCK = {
    "accounts.google.com", "appleid.apple.com", "login.microsoftonline.com",
    "youtube.com", "youtube-nocookie.com", "googlevideo.com", "ytimg.com",
    "maps.googleapis.com", "gstatic.com", "fonts.googleapis.com", "fonts.gstatic.com",
    "cdnjs.cloudflare.com", "cdn.jsdelivr.net", "unpkg.com",
}

# Our own additions: major ad and tracking hosts that EasyList blocks only through
# path or popup rules, so a host-level extraction misses them. Every entry serves
# ads or tracking only (no content, no sign-in), so blocking the whole host is safe.
OWN_HOSTS = {
    "doubleclick.net", "googlesyndication.com", "adservice.google.com",
    "appsflyer.com", "ads-twitter.com", "snap.licdn.com",
}

# Options that make a rule something other than "block this host as a third party".
REJECT_OPTIONS = ("domain=", "popup", "document", "denyallow", "redirect", "removeparam", "csp", "header")

SECTION_RE = re.compile(r"^! \*\*\* easylist:(.+?) \*\*\*")
HOST_RULE_RE = re.compile(r"^\|\|([a-z0-9.-]+)\^(?:\$(.*))?$")
HOST_RE = re.compile(r"^(?=.{1,253}$)([a-z0-9-]{1,63}\.)+[a-z][a-z0-9-]{1,62}$")

GZIP_LIMIT_BYTES = 300 * 1024


def read_source(path_or_url: str) -> list[str]:
    if path_or_url.startswith("https://"):
        with urllib.request.urlopen(path_or_url, timeout=60) as response:
            return response.read().decode("utf-8").splitlines()
    return Path(path_or_url).read_text(encoding="utf-8").splitlines()


def extract_hosts(lines: list[str], sections: set[str]) -> set[str]:
    hosts: set[str] = set()
    current = None
    for line in lines:
        marker = SECTION_RE.match(line)
        if marker:
            current = marker.group(1)
            continue
        if current not in sections:
            continue
        match = HOST_RULE_RE.match(line.strip().lower())
        if not match:
            continue
        host, options = match.group(1), match.group(2) or ""
        if any(option in options for option in REJECT_OPTIONS):
            continue
        if HOST_RE.match(host):
            hosts.add(host)
    return hosts


def load_popular(path: str) -> set[str]:
    domains = set()
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        parts = line.strip().split(",")
        if len(parts) >= 2:
            domains.add(parts[1].lower())
    return domains


def host_or_parent_in(host: str, domains: set[str]) -> bool:
    labels = host.split(".")
    return any(".".join(labels[i:]) in domains for i in range(len(labels) - 1))


def is_never_block(host: str) -> bool:
    return any(host == safe or host.endswith("." + safe) for safe in NEVER_BLOCK)


def drop_covered_subdomains(hosts: set[str]) -> list[str]:
    """A listed parent already blocks its subdomains (data-model rule 3)."""
    kept = []
    for host in hosts:
        labels = host.split(".")
        if not any(".".join(labels[i:]) in hosts for i in range(1, len(labels) - 1)):
            kept.append(host)
    return sorted(kept)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--easyprivacy", default=EASYPRIVACY_URL)
    parser.add_argument("--easylist", default=EASYLIST_URL)
    parser.add_argument("--popularity", help="Tranco-style CSV (rank,domain) used as a membership filter")
    parser.add_argument(
        "--out",
        default=str(Path(__file__).resolve().parents[2] / "app/src/main/assets/protection/tracker_hosts.txt"),
    )
    args = parser.parse_args()

    privacy = extract_hosts(read_source(args.easyprivacy), EASYPRIVACY_SECTIONS)
    easylist = read_source(args.easylist)
    ads = extract_hosts(easylist, EASYLIST_SECTIONS)
    if args.popularity:
        popular = load_popular(args.popularity)
        ads |= {h for h in extract_hosts(easylist, EASYLIST_ADSERVER_SECTIONS) if host_or_parent_in(h, popular)}
    hosts = drop_covered_subdomains({h for h in privacy | ads | OWN_HOSTS if not is_never_block(h)})

    header = [
        "# ThirtySix Browser tracker and ad-server hosts.",
        "# Derived from EasyPrivacy and EasyList by The EasyList authors (https://easylist.to/),",
        "# used under CC BY-SA 3.0 (https://creativecommons.org/licenses/by-sa/3.0/).",
        "# Modified: host-level subset; ad-server hosts limited to popular domains",
        "# (tools/protection-lists/build_tracker_hosts.py), plus a few ThirtySix-chosen ad hosts.",
        "# This file is itself licensed CC BY-SA 3.0.",
        f"# Generated: {datetime.date.today().isoformat()} - hosts: {len(hosts)}",
    ]
    body = "\n".join(header + hosts) + "\n"
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(body, encoding="utf-8")

    gz = len(gzip.compress(body.encode("utf-8"), compresslevel=9))
    print(f"easyprivacy={len(privacy)} easylist={len(ads)} written={len(hosts)} raw={len(body)} gzip={gz}")
    if gz > GZIP_LIMIT_BYTES:
        print(f"ERROR: gzip size {gz} exceeds {GZIP_LIMIT_BYTES} (research R9)", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
