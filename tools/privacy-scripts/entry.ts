// Spec 024 research R7 — ThirtySix's own entry for DuckDuckGo autoconsent (MPL-2.0).
// This file is ThirtySix code; it only imports the unmodified autoconsent package.
//
// The built bundle is wrapped by build.sh as `(function(cfg){ ... })`; the app's
// PrivacyScriptComposer calls it with a frozen object `{v, s, x}` (research R5):
//   x = registrable domains with protection off for this WebView.
// Nothing is written to `window`, and nothing is ever sent to the app (Constitution §II-4):
// the message sender below is a no-op.

import AutoConsent, { filterCompactRules } from '@duckduckgo/autoconsent';
import rules from './build/generic-rules.json';

declare const cfg: { v: number; s: string; x: string[] };

function topHost(): string {
    const ancestors = (location as Location & { ancestorOrigins?: DOMStringList }).ancestorOrigins;
    if (ancestors && ancestors.length > 0) {
        try {
            return new URL(ancestors[ancestors.length - 1]).hostname.toLowerCase();
        } catch (e) {
            // fall through to this frame's own host
        }
    }
    return location.hostname.toLowerCase();
}

function protectionOff(host: string): boolean {
    return cfg.x.some((site) => host === site || host.endsWith('.' + site));
}

if (/^https?:$/.test(location.protocol) && !protectionOff(topHost())) {
    const mainFrame = window.top === window;
    // Never 'optIn', never a heuristic mode other than 'off': upstream's 'tier2' clicks Accept
    // on banners without a reject button (research R7, FR-024).
    const config = {
        enabled: true,
        autoAction: 'optOut' as const,
        disabledCmps: [],
        isMainWorld: true,
        enablePrehide: false,
        enableCosmeticRules: false,
        enableGeneratedRules: false,
        enableHeuristicDetection: false,
        heuristicMode: 'off' as const,
        enablePopupMutationObserver: false,
        performanceLoggingEnabled: false,
        visualTest: false,
        logs: {
            lifecycle: false,
            rulesteps: false,
            detectionsteps: false,
            evals: false,
            errors: false,
            messages: false,
            waits: false,
        },
    };
    const bundle = {
        autoconsent: [],
        compact: filterCompactRules(rules as Parameters<typeof filterCompactRules>[0], {
            url: location.href,
            mainFrame,
        }),
    };
    // eslint-disable-next-line no-new
    new AutoConsent(async () => {}, config, bundle);
}
