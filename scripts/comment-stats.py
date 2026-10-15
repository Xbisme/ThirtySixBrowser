#!/usr/bin/env python3
"""Comment statistics counted the way CommentPolicyTest counts them.

Usage: scripts/comment-stats.py <source-folder> [--list]
"""
import os
import sys

MAX_BLOCK_LINES = 5
MAX_DENSITY = 0.15
MIN_LINES_FOR_DENSITY = 30


def stats(source):
    comment = code = block = longest = 0
    in_block = False
    for raw in source.splitlines():
        line = raw.strip()
        is_comment = in_block or line.startswith("//") or line.startswith("/*")
        skipped = not line or line.startswith("import ") or line.startswith("package ")
        if line.startswith("/*") and "*/" not in line:
            in_block = True
        if in_block and "*/" in line:
            in_block = False
        if skipped or (is_comment and not line.lstrip("/*").strip()):
            continue
        if is_comment:
            comment += 1
            block += 1
            longest = max(longest, block)
        else:
            code += 1
            block = 0
    return comment, code, longest


def main():
    root = sys.argv[1]
    listing = "--list" in sys.argv
    total_comment = total_code = dense = long_blocks = files = 0
    for folder, _, names in os.walk(root):
        for name in sorted(names):
            if not name.endswith(".kt"):
                continue
            path = os.path.join(folder, name)
            with open(path, encoding="utf-8") as handle:
                comment, code, longest = stats(handle.read())
            files += 1
            total_comment += comment
            total_code += code
            too_dense = comment + code >= MIN_LINES_FOR_DENSITY and comment / (comment + code) > MAX_DENSITY
            too_long = longest > MAX_BLOCK_LINES
            dense += too_dense
            long_blocks += too_long
            if listing and (too_dense or too_long):
                print(f"{path}\tdensity={comment / max(1, comment + code):.0%}\tlongest={longest}")
    share = total_comment / max(1, total_comment + total_code)
    print(f"files={files} comment_lines={total_comment} code_lines={total_code} "
          f"share={share:.1%} files_over_density={dense} files_over_block={long_blocks}")


if __name__ == "__main__":
    main()
