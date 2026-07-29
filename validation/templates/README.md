# Validation result records

Copy `result-manifest.template.yaml` once per execution. It maps the VAL item to
RF/RNF IDs and records frozen criteria, raw/derived artifacts and checksums,
results, deviations, incidents, limitations, and independent review.
`result-manifest.schema.json` validates a JSON serialization of the template.
The shipped example uses valid VAL-04/PV/RF/RNF identifiers and consistently
defaults execution, criterion, and conclusion to `NOT_RUN`; replace its example
mapping when creating another experiment.

`traceability-matrix.csv` is an initial protocol-v0.1 mapping, not stakeholder
approval. All entries remain `NOT_RUN`: scripts generate evidence only when
executed, and a zero exit code does not establish compliance.

Preserve raw output in the approved evidence store, review it for secrets/PII,
and record controlled URIs plus SHA-256 checksums. Regtest results are
provisional pending D-05; testnet is smoke only. Never use real funds or PII.
