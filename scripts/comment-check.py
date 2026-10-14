#!/usr/bin/env python3
"""Checks a comment-only edit of Kotlin files against the comment policy.

Usage: scripts/comment-check.py [--base REV] FILE_OR_DIR...

For every .kt file it reports:
  code-changed   the code (comments removed, whitespace collapsed) differs from REV (default HEAD)
  forbidden      a comment matches a ForbiddenComment pattern of config/detekt/detekt.yml
  long-block     a comment block of more than 5 lines (same counting as CommentPolicyTest)
  dense          more than 15 % comment lines in a file of 30+ lines (production sources only)
Exit status 1 when anything is reported.
"""
import os
import re
import subprocess
import sys

FORBIDDEN = [
    r"\b[Ss]pec[ -]?\d{2,}\b",
    r"\b(FR|NFR|SC|US|AC|INV)-\d+",
    r"\bT\d{3}\b",
    r"\b(spec|plan|tasks|research|data-model|quickstart)\.md\b",
    r"(?i)\bconstitution\b|§",
    r"(?i)\b(previously|used to be|was changed|changed from|before this change|old implementation)\b",
    "[⚠✅❌❗\U0001F6A8\U0001F525]",
    r"\b[A-Z]{2,}(?: [A-Z]{2,}){3,}\b",
    r"TODO(?!\([A-Z][A-Z0-9]*-\d+\))",
    r"FIXME|STOPSHIP",
]


def split_code_and_comments(text):
    """Returns (code without comments, list of comment texts), honouring string literals."""
    code, comments, i, n = [], [], 0, len(text)
    while i < n:
        if text.startswith('"""', i):
            j = text.find('"""', i + 3)
            j = n if j < 0 else j + 3
            code.append(text[i:j]); i = j
        elif text[i] == '"':
            j = i + 1
            while j < n and text[j] != '"' and text[j] != "\n":
                j += 2 if text[j] == "\\" else 1
            code.append(text[i:j + 1]); i = j + 1
        elif text[i] == "'" and i + 2 < n and (text[i + 2] == "'" or text[i + 1] == "\\"):
            j = text.find("'", i + 2 if text[i + 1] == "\\" else i + 1)
            code.append(text[i:j + 1]); i = j + 1
        elif text.startswith("//", i):
            j = text.find("\n", i)
            j = n if j < 0 else j
            comments.append(text[i:j]); i = j
        elif text.startswith("/*", i):
            depth, j = 1, i + 2
            while j < n and depth:
                if text.startswith("/*", j):
                    depth += 1; j += 2
                elif text.startswith("*/", j):
                    depth -= 1; j += 2
                else:
                    j += 1
            comments.append(text[i:j]); i = j
        else:
            code.append(text[i]); i += 1
    return "".join(code), comments


def normalized_code(text):
    code, _ = split_code_and_comments(text)
    return re.sub(r"\s+", " ", code).strip()


def stats(text):
    comment = code = block = longest = 0
    in_block = False
    for raw in text.splitlines():
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
            comment += 1; block += 1; longest = max(longest, block)
        else:
            code += 1; block = 0
    return comment, code, longest


def files_of(args):
    for a in args:
        if os.path.isdir(a):
            for d, _, fs in os.walk(a):
                for f in sorted(fs):
                    if f.endswith(".kt"):
                        yield os.path.join(d, f)
        elif a.endswith(".kt"):
            yield a


def main():
    args = sys.argv[1:]
    base = "HEAD"
    if args[:1] == ["--base"]:
        base, args = args[1], args[2:]
    problems = 0
    for path in files_of(args):
        text = open(path, encoding="utf-8").read()
        rel = os.path.relpath(path)
        old = subprocess.run(["git", "show", f"{base}:{rel}"], capture_output=True, text=True)
        if old.returncode == 0 and normalized_code(old.stdout) != normalized_code(text):
            print(f"code-changed  {rel}"); problems += 1
        _, comments = split_code_and_comments(text)
        for c in comments:
            for pat in FORBIDDEN:
                m = re.search(pat, c)
                if m:
                    print(f"forbidden     {rel}: {m.group(0)!r} in {c.strip()[:70]!r}"); problems += 1
                    break
        comment, code, longest = stats(text)
        if longest > 5:
            print(f"long-block    {rel}: {longest} lines"); problems += 1
        production = "/src/main/" in path or "/src/debug/" in path
        if production and comment + code >= 30 and comment / (comment + code) > 0.15:
            print(f"dense         {rel}: {comment / (comment + code):.0%}"); problems += 1
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
