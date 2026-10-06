## 1. Decision contract

- [x] 1.1 In `DeleteDecision.actions()`, drop the `otherCategories > 0` condition and update the doc comment (removal at the last location keeps the skin, uncategorized); verify `DeleteDecisionTest` compiles against the new contract
- [x] 1.2 Flip `popup collapses to one action at the skin's last location` to expect both actions, and keep the derived-view and multi-category tests green; verify with `./gradlew :1.21.11:test --tests "...DeleteDecisionTest"`

## 2. Gates

- [x] 2.1 Run `./gradlew build test detektAll`; verify all pass
- [x] 2.2 Run `openspec validate always-offer-remove-card --strict`; verify valid
