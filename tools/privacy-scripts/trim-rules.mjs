// Spec 024 research R7 — keep only the generic and frame rules of autoconsent's compact ruleset.
// Site-specific rules are dropped to keep the per-frame parse cost low (SC-010). Generated
// `auto_*` rules stay in the file but never run (enableGeneratedRules: false).
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';

const src = JSON.parse(readFileSync('node_modules/@duckduckgo/autoconsent/rules/compact-rules.json', 'utf8'));
const { v, s, r, index } = src;
const ruleEnd = Math.max(index.genericRuleRange[1], index.frameRuleRange[1]);
const stringEnd = Math.max(index.genericStringEnd, index.frameStringEnd);
const out = {
    v,
    s: s.slice(0, stringEnd),
    r: r.slice(0, ruleEnd),
    index: {
        genericRuleRange: index.genericRuleRange,
        frameRuleRange: index.frameRuleRange,
        specificRuleRange: [ruleEnd, ruleEnd],
        genericStringEnd: index.genericStringEnd,
        frameStringEnd: index.frameStringEnd,
    },
};
mkdirSync('build', { recursive: true });
writeFileSync('build/generic-rules.json', JSON.stringify(out));
console.log(`rules kept: ${out.r.length} of ${r.length}, strings: ${out.s.length} of ${s.length}`);
