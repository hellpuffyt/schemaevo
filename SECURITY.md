# Security policy

## Supported versions

Only the latest commit on `main` is supported.

## Reporting a vulnerability

Please report security issues privately through GitHub:
https://github.com/hellpuffyt/schemaevo/security/advisories/new

Do not open a public issue for a vulnerability. Include the schemaevo version
or commit, the command you ran, and (if possible) the schema files that
trigger the problem.

## Scope

schemaevo reads the local schema files it is given and never makes network
calls. Relevant issues include crashes or unbounded resource use on crafted
schema files, and vulnerabilities in its bundled dependencies.
