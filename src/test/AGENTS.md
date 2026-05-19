# Test Guidance

- Only add integration tests in this tree. Do not add unit tests.
- Prefer behavioural tests that exercise public interfaces and observable outcomes.
- Avoid tests coupled to implementation details; implementation details change often and tests should survive refactors.
- Follow red/green/refactor TDD: add one failing behavioural integration test, make the minimal production change to pass, then refactor while green.
