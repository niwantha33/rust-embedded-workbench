<!-- SPDX-License-Identifier: Apache-2.0 -->
# Security policy

This repository is an early development bootstrap and is **not** qualified for safety-critical or production use.

Treat tool output, ELF files, SVD packages, board metadata and firmware projects as untrusted until validated. A future command runner must use argument vectors, bounded timeouts and explicit working directories. Flashing, erasing and target reset require clear user action and verified device identity.

Do not file public issues containing credentials, private firmware or sensitive logs. Contact maintainers privately via the repository owner's GitHub profile regarding suspected vulnerabilities.
