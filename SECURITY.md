# Security policy

## Supported versions

Security fixes are developed against the latest source on `main` and are intended for the latest Stable release. Older releases and Beta builds may not receive backported fixes.

The browser engine is Mozilla GeckoView. GeckoView security fixes reach Cat Browser only after the dependency is reviewed, updated, tested and shipped in a new application release.

## Reporting a vulnerability

**Please do not publish an unpatched security vulnerability in a public GitHub issue, discussion or pull request.**

Use GitHub's private vulnerability reporting for this repository when it is available:

1. Open the repository's **Security** tab.
2. Select **Advisories** or **Report a vulnerability**.
3. Submit the report privately to the maintainers.

[Open the repository's private vulnerability reporting page](https://github.com/snowgemm/Cat-Browser/security/advisories/new).

If private vulnerability reporting is unavailable, do not post exploit details publicly. Contact the repository owner through the GitHub profile and ask for a private reporting channel.

Include, where safe:

- The affected Cat Browser version or commit and GeckoView version.
- Device model, Android version and ABI.
- A concise description of the security impact and affected component.
- Minimal reproduction steps and relevant, redacted logs.
- Any known workaround or mitigation.

Do not include passwords, access tokens, cookies, private browsing URLs, personal data or other secrets in the report.

## Handling and disclosure

The maintainers will review reports and coordinate a fix and disclosure when practical. No response or remediation deadline is promised. Please allow maintainers an opportunity to investigate and prepare a fix before public disclosure.

A successful build or CI run is not a security certification. Browser engine, extension, permission, download, updater and AI integration changes require focused security review and appropriate device testing.
