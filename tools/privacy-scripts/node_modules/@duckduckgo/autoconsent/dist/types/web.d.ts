import { MessageSender, AutoCMP, RuleBundle, Config, ConsentState } from './types';
import { AutoConsentCMPRule } from './rules';
import { BackgroundMessage } from './messages';
import { DomActions } from './dom-actions';
export { snippets as evalSnippets } from './eval-snippets';
export { filterCompactRules, encodeRules, decodeRules } from './encoding';
export type { CompactRuleStep, CompactCMPRuleStep, CompactCMPRule, CompactCMPRuleset, IndexedCMPRuleset } from './encoding';
export type { ConsentState, Config, RuleBundle } from './types';
export type * from './rules';
export type * from './messages';
export declare function filterCMPs(rules: AutoCMP[], config: Config): AutoCMP[];
export default class AutoConsent {
    #private;
    id: string;
    rules: AutoCMP[];
    foundCmp?: AutoCMP;
    state: ConsentState;
    domActions: DomActions;
    sendContentMessage: MessageSender;
    protected focusedElement?: HTMLElement;
    constructor(sendContentMessage: MessageSender, config?: Partial<Config> | null, declarativeRules?: RuleBundle | null);
    get config(): Config;
    initialize(config: Partial<Config>, declarativeRules: RuleBundle | null): void;
    get shouldPrehide(): boolean;
    saveFocus(): void;
    restoreFocus(): void;
    addDynamicRules(): void;
    parseDeclarativeRules(declarativeRules: RuleBundle): void;
    addDeclarativeCMP(ruleset: AutoConsentCMPRule): void;
    start(): void;
    _start(): Promise<boolean>;
    findCmp(retries: number): Promise<AutoCMP[]>;
    detectHeuristics(): void;
    /**
     * Detect if a CMP has a popup open. Fullfils with the CMP if a popup is open, otherwise rejects.
     */
    detectPopup(cmp: AutoCMP): Promise<AutoCMP>;
    /**
     * Detect if any of the CMPs has a popup open. Returns a list of CMPs with open popups.
     */
    detectPopups(cmps: AutoCMP[], onFirstPopupAppears: (cmp: AutoCMP) => Promise<unknown>): Promise<AutoCMP[]>;
    handlePopup(cmp: AutoCMP): Promise<boolean>;
    doOptOut(): Promise<boolean>;
    doOptIn(): Promise<boolean>;
    doSelfTest(): Promise<boolean>;
    waitForPopup(cmp: AutoCMP, retries?: number, interval?: number): Promise<boolean>;
    prehideElements(): boolean;
    undoPrehide(): void;
    updateState(change: Partial<ConsentState>): void;
    receiveMessageCallback(message: BackgroundMessage): Promise<void>;
    measurePerformance(): {
        detectHeuristics: number[];
        heuristicDetector: number[];
        findCmpSiteSpecific: number[];
        findCmpGeneric: number[];
        findCmpHeuristic: number[];
        parseDeclarativeRules: number[];
    };
}
