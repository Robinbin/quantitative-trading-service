---
name: review
description: Code review for changed files — checks quality, security, tests, and Spring Boot conventions
argument-hint: [file-or-PR-number]
disable-model-invocation: true
allowed-tools: [Read, Glob, Grep, Bash]
---

## Review Context

Current branch: !`git branch --show-current`

Changed files:
!`git diff --name-only HEAD 2>/dev/null || git diff --name-only --cached`

Diff:
!`git diff HEAD 2>/dev/null || git diff --cached`

---

Perform a thorough code review of the changes above (or `$ARGUMENTS` if a specific file or PR number is given).

Evaluate each changed file against the following checklist and produce a structured report.

---

## Checklist

### 1. Correctness
- Logic errors, off-by-one issues, wrong conditions
- Null / empty checks where data may be absent
- Exception handling — are failures logged and propagated correctly?
- Thread safety for shared state

### 2. Code Quality
- Single Responsibility — classes and methods do one thing
- Method length ≤ 30 lines as a guideline
- No duplicated logic that should be extracted
- Names are clear and intention-revealing
- No magic numbers / strings without named constants

### 3. Spring Boot Conventions (Java / Spring)
- `@Service`, `@Repository`, `@Component`, `@Controller` used appropriately
- Constructor injection preferred over field injection
- `@Transactional` boundaries are correct
- Config values read via `@ConfigurationProperties` or `@Value`, not hardcoded
- REST endpoints follow RESTful naming and return correct HTTP status codes
- Caching annotations (`@Cacheable`, `@CacheEvict`) used correctly

### 4. Security
- No hardcoded secrets, passwords, or API keys
- SQL / JPQL uses parameterised queries, not string concatenation
- User input is validated before use
- Sensitive data not logged at INFO level
- OWASP Top 10 issues (XSS, injection, insecure deserialisation, etc.)

### 5. Performance
- No N+1 query patterns
- Heavy computations not done on the main thread unnecessarily
- Caching applied where data is stable
- Collections sized appropriately

### 6. Tests
- New logic has corresponding unit tests
- Edge cases (empty list, null input, exception paths) are covered
- Test names clearly describe the scenario (`methodName_shouldBehaviour_whenCondition`)
- No `@Disabled` or skipped tests without explanation

### 7. Documentation
- Public API methods have Javadoc if non-trivial
- Inline comments explain *why*, not *what*
- No stale/misleading comments

---

## Output Format

Produce the report in this structure:

```
## Summary
<1-3 sentence overall assessment>

## Issues

### Critical  (must fix before merge)
- [File:Line] Description + suggested fix

### Major  (should fix)
- [File:Line] Description + suggested fix

### Minor  (nice to have)
- [File:Line] Description + suggestion

## Positives
- What was done well

## Verdict
[ ] Approve   [ ] Approve with minor comments   [ ] Request changes
```

If `$ARGUMENTS` is a PR number, fetch the PR diff with `gh pr diff $ARGUMENTS` and review that instead of the local diff.
